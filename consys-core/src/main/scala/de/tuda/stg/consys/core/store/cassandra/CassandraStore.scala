package de.tuda.stg.consys.core.store.cassandra

import java.io._
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import com.datastax.oss.driver.api.core.{CqlSession, DriverTimeoutException}
import com.datastax.oss.driver.api.core.`type`.codec.TypeCodecs
import com.datastax.oss.driver.api.core.cql.{BatchStatement, BatchType}
import com.datastax.oss.driver.api.core.servererrors.InvalidQueryException
import com.datastax.oss.driver.api.querybuilder.QueryBuilder
import de.tuda.stg.consys.core.Address
import de.tuda.stg.consys.core.store.exceptions.ObjectNotAvailableException
import de.tuda.stg.consys.core.store.locking.{ZookeeperLockingStoreExt, ZookeeperStoreExt}
import de.tuda.stg.consys.core.store.{DistributedStore, LockingStore}
import org.apache.curator.framework.{CuratorFramework, CuratorFrameworkFactory}
import org.apache.curator.retry.ExponentialBackoffRetry
import scala.concurrent.TimeoutException
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.reflect.runtime.universe.TypeTag


/**
 * Created on 10.12.19.
 *
 * @author Mirko Köhler
 */
trait CassandraStore extends DistributedStore
	with ZookeeperStoreExt
	with ZookeeperLockingStoreExt {

	//TODO: Timeout is not used. How to integrate into Datastax driver?

	override final type Addr = String
	override final type ObjType = java.io.Serializable

	override final type TxContext = CassandraTransactionContext

	override final type RawType[T <: ObjType] = CassandraObject[T]
	override final type RefType[T <: ObjType] = CassandraHandler[T]

	protected[store] val cassandraSession : CqlSession

	override def transaction[U](code : TxContext => Option[U]) : Option[U] = {
		val tx = CassandraTransactionContext(this)
		CassandraStores.currentTransaction.withValue(tx) {
			try {
				code(tx) match {
					case None => None
					case res@Some(_) =>
						res
				}
			} finally {
				tx.commit()
			}
		}
	}


	override def close(): Unit = {
		super.close()
		cassandraSession.close()
	}


	override def name : String = s"node@${cassandraSession.getContext.getSessionName}"


	def initializeStore() : Unit = {
		CassandraBinding.initialize()
	}

	override protected[store] def enref[T <: ObjType : TypeTag](obj : CassandraObject[T]) : CassandraHandler[T] =
		new CassandraHandler[T](obj.addr, obj.consistencyLevel)


	/**
	 * This object is used to communicate with Cassandra, i.e. writing and reading data from keys.
	 */
	private[cassandra] final object CassandraBinding {
		private val keyspaceName : String = "consys_experimental"
		private val objectTableName : String = "objects"

		/* Initialize tables, if not available... */



		private[CassandraStore] def initialize(): Unit = {
			try {
				val res = cassandraSession.execute(s"""DROP KEYSPACE IF EXISTS $keyspaceName""")
				res.all()
			} catch {
				case e : DriverTimeoutException => println("timeout 1")
			}

			try {
				val res = cassandraSession.execute(
					s"CREATE KEYSPACE IF NOT EXISTS $keyspaceName WITH REPLICATION = {'class':'SimpleStrategy', 'replication_factor':3}")
				res.all()
			} catch {
				case e : DriverTimeoutException => println("timeout 2")
			}

			try {
				val res = cassandraSession.execute(
					s"CREATE TABLE IF NOT EXISTS $keyspaceName.$objectTableName (addr text primary key, state blob)")
				res.all()
			} catch {
				case e : DriverTimeoutException => println("timeout 3")
			}
		}

		private[cassandra] def writeObject[T <: Serializable](addr : String, obj : T, clevel : CLevel, timestamp : Long) : Unit = {
			import QueryBuilder._

			val query = insertInto(keyspaceName, objectTableName)
				.value("addr", literal(addr))
				.value("state", literal(CassandraStore.serializeObject(obj)))
  			.usingTimestamp(timestamp)
				.build()
				.setConsistencyLevel(clevel)

			//TODO: Add failure handling
			cassandraSession.execute(query)
		}

		private[cassandra] def writeObjects(objs : Iterable[(String, _)], clevel : CLevel, timestamp : Long) : Unit = {
			import QueryBuilder._

			cassandraSession.execute(s"USE $keyspaceName")

			val batch = BatchStatement.builder(BatchType.LOGGED)
			for (obj <- objs) {
				val query = insertInto(s"$objectTableName")
					.value("addr", literal(obj._1))
					.value("state", literal(CassandraStore.serializeObject(obj._2.asInstanceOf[Serializable])))
  				.usingTimestamp(timestamp)
					.build()
					.setConsistencyLevel(clevel)

				batch.addStatement(query)
			}

			//TODO: Add failure handling
			cassandraSession.execute(batch.build().setConsistencyLevel(clevel))
		}


		private[cassandra] def readObject[T <: Serializable : TypeTag](addr : String, clevel : CLevel) : T = {
			import QueryBuilder._

			val query = selectFrom(keyspaceName, objectTableName)
				.all()
				.whereColumn("addr").isEqualTo(literal(addr))
				.build()
				.setConsistencyLevel(clevel)

			//TODO: Add failure handling
			val startTime = System.nanoTime()
			while (System.nanoTime() < startTime + timeout.toNanos) {
				val response = cassandraSession.execute(query)

				response.one() match {
					case null =>  //the address has not been found. retry.
					case row =>
						return CassandraStore.deserializeObject[T](row.get("state", TypeCodecs.BLOB))
				}

				Thread.sleep(200)
			}

			throw ObjectNotAvailableException(addr)

		}
	}


}

object CassandraStore {

	case class AddrNotAvailableException(addr : String) extends Exception(s"address <$addr> not available")

	def fromAddress(host : String, cassandraPort : Int, zookeeperPort : Int, withTimeout : FiniteDuration = Duration(10, "s")) : CassandraStore = {

		class CassandraStoreImpl(
			override val cassandraSession : CqlSession,
			override val curator : CuratorFramework,
			override val timeout : FiniteDuration
    ) extends CassandraStore


		new CassandraStoreImpl(
			cassandraSession = CqlSession.builder()
					.addContactPoint(InetSocketAddress.createUnresolved(host, cassandraPort))
					.withLocalDatacenter("datacenter1")
					.build(),
			curator = CuratorFrameworkFactory
				.newClient(s"$host:$zookeeperPort", new ExponentialBackoffRetry(250, 3)),
			timeout = withTimeout
		)
	}


	/* Helper methods */

	private[CassandraStore] def serializeObject[T <: Serializable](obj : T) : ByteBuffer = {
		require(obj != null)

		val bytesOut = new ByteArrayOutputStream()
		val oos = new ObjectOutputStream(bytesOut)
		try {
			oos.writeObject(obj)
			oos.flush()
			return ByteBuffer.wrap(bytesOut.toByteArray)
		} finally {
			bytesOut.close()
			oos.close()
		}
		throw new NotSerializableException(obj.getClass.getName)
	}

	private[CassandraStore] def deserializeObject[T <: Serializable](buffer : ByteBuffer) : T = {
		require(buffer != null)

		val bytesIn = new ByteArrayInputStream(buffer.array())
		val ois = new ObjectInputStream(bytesIn)

		try {
			return ois.readObject().asInstanceOf[T]
		} finally {
			bytesIn.close()
			ois.close()
		}
		throw new NotSerializableException()
	}

}
