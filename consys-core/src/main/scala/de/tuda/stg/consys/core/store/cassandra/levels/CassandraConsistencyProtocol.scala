package de.tuda.stg.consys.core.store.cassandra.levels

import de.tuda.stg.consys.core.store.cassandra.CassandraStore
import de.tuda.stg.consys.core.store.{Store, StoreConsistencyLevel, StoreConsistencyProtocol}

import scala.reflect.ClassTag

/**
 * Created on 03.03.20.
 *
 * @author Mirko Köhler
 */
trait CassandraConsistencyProtocol extends StoreConsistencyProtocol {
	override type StoreType = CassandraStore
	override type Level = CassandraConsistencyLevel

	def writeRaw[T <: StoreType#ObjType : ClassTag](addr : StoreType#Addr, obj : T, txContext : StoreType#TxContext) : StoreType#RawType[T]
	def readRaw[T <: StoreType#ObjType : ClassTag](addr : StoreType#Addr, txContext : StoreType#TxContext) : StoreType#RawType[T]
}
