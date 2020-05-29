package de.tuda.stg.consys.core.store.akka

import com.datastax.oss.driver.api.core.{ConsistencyLevel => CLevel}
import de.tuda.stg.consys.core.store.exceptions.ObjectNotAvailableException
import de.tuda.stg.consys.core.store.{StoreConsistencyLevel, StoreConsistencyModel}
import scala.reflect.runtime.universe._

/**
 * Created on 11.12.19.
 *
 * @author Mirko Köhler
 */
case object Strong extends StoreConsistencyLevel {
	override type StoreType = AkkaStore
	override def toModel(store : StoreType) : StoreConsistencyModel {type StoreType = Strong.this.StoreType} = new Model(store)

	private class Model(val store : AkkaStore) extends StoreConsistencyModel {
		override type StoreType = AkkaStore

		override def toLevel : StoreConsistencyLevel = Strong

		override def replicateRaw[T <: StoreType#ObjType : TypeTag](addr : StoreType#Addr, obj : T, txContext : StoreType#TxContext) : StoreType#RawType[T] = {
			txContext.acquireLock(addr)
			new StrongAkkaObject(addr, obj, store, txContext)
		}

		@throws[ObjectNotAvailableException]
		override def lookupRaw[T <: StoreType#ObjType : TypeTag](addr : StoreType#Addr, txContext : StoreType#TxContext) : StoreType#RawType[T] = {
			txContext.acquireLock(addr)
			val obj = store.AkkaBinding.getObject(addr)[T]
			assert(obj.isInstanceOf[StrongAkkaObject])
			obj
		}
	}

	private class StrongAkkaObject[T <: java.io.Serializable : TypeTag](
		override val addr : String,
		override val state : T,
		store : StoreType,
		txContext : StoreType#TxContext
	) extends AkkaObject[T] {
		override def consistencyLevel : StoreConsistencyLevel { type StoreType = AkkaStore } = Strong

		override def writeToStore(store : CassandraStore) : Unit =
			store.CassandraBinding.writeObject(addr, state, CLevel.ALL, txContext.timestamp)
	}

}