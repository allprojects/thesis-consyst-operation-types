package de.tuda.stg.consys.core.store.akka

import com.sun.tools.javac.code.TypeTag
import de.tuda.stg.consys.core.store.akka.AkkaObject.SyncStrategy
import de.tuda.stg.consys.core.store.{ReflectiveStoredObject, StoreConsistencyLevel}

private[akka] abstract class AkkaObject[T <: java.io.Serializable : TypeTag] extends ReflectiveStoredObject[AkkaStore, T] {
  def addr : AkkaStore#Addr
  def consistencyLevel : StoreConsistencyLevel { type StoreType = AkkaStore }
  def syncStrategy : SyncStrategy

  def invoke[R](methodId : String, args : Seq[Seq[Any]]) : R = {
    ReflectiveAccess.doInvoke(methodId, args)
  }
}

private[akka] object AkkaObject {
  class SyncStrategy {}
}