package de.tuda.stg.consys.core.store.akka

import de.tuda.stg.consys.core.store.{Handler, StoreConsistencyLevel}
import scala.reflect.runtime.universe.TypeTag

class AkkaHandler[T <: java.io.Serializable : TypeTag](
    val addr : String,
    val level : StoreConsistencyLevel {type StoreType = AkkaStore}
  ) extends Handler[AkkaStore, T] with Serializable {

  override def resolve(tx : => AkkaStore#TxContext) : AkkaStore#RawType[T] = {
    tx.lookupRaw[T](addr, level)
  }

  /* This method is for convenience use in transactions */
  def resolve() : AkkaStore#RawType[T] =
    resolve(CassandraStores.getCurrentTransaction)
}
