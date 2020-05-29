package de.tuda.stg.consys.core.store.akka

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import de.tuda.stg.consys.core.Address
import de.tuda.stg.consys.core.akka.AkkaReplicaSystem
import de.tuda.stg.consys.core.store.DistributedStore
import de.tuda.stg.consys.core.store.akka.AkkaObject.SyncStrategy
import de.tuda.stg.consys.core.store.akka.AkkaStore.SyncStrategy
import de.tuda.stg.consys.core.store.akka.AkkaTransactionContext.CachedEntry
import de.tuda.stg.consys.core.store.exceptions.ObjectNotAvailableException
import scala.collection.mutable

class AkkaStore extends DistributedStore {
  override final type Addr = String
  override final type ObjType = java.io.Serializable

  override final type TxContext = AkkaTransactionContext

  override final type RawType[T <: ObjType] = AkkaObject[T]
  override final type RefType[T <: ObjType] = AkkaHandler[T]

  /* The actor system to use for this replica system */
  def actorSystem : ActorSystem

  /*Other replicas known to this replica.*/
  def otherReplicas : Set[Address]

  override def transaction[U](code : TxContext => Option[U]) : Option[U] = {
    val tx = AkkaTransactionContext(this)
    AkkaStores.currentTransaction.withValue(tx) {
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
    actorSystem.terminate()
  }


  override def name : String = s"node@${actorSystem.name}"


  private[akka] final object AkkaBinding {
    /*The actor that is used to communicate with this replica.*/
    private final val replicaActor : ActorRef = actorSystem.actorOf(Props(classOf[ReplicaActor], this), s"actor:$name")

    /* The objects stored in the local replica */
    private final val localObjects : mutable.Map[Addr, StoreEntry] = mutable.HashMap.empty

    @throws[ObjectNotAvailableException]
    def getObject[T <: ObjType](addr : Addr) : RawType[T] = {
      val startTime = System.nanoTime()
      while (System.nanoTime() < startTime + timeout.toNanos) {
        localObjects.get(addr) match {
          case None =>  //the address has not been found. retry.
          case Some(entry) => return entry.obj.asInstanceOf[AkkaObject[T]]
        }

        Thread.sleep(200)
      }

      throw ObjectNotAvailableException(addr)
    }

    def mergeLocalState(timestamp : Long, cache : Map[Addr, CachedEntry[_]]) : Unit = {
      for (entry <- cache) {
        //TODO: Implement state merges
        localObjects.put(entry._1, StoreEntry(timestamp, entry._2.obj, entry._2.sync))
      }
    }


    /* An entry of the local replica */
    private case class StoreEntry(var timeStamp : Long, obj : AkkaObject[_], sync : SyncStrategy)


    private class ReplicaActor extends Actor {

      override def receive : Receive = {
        ???
      }
    }
  }

}

object AkkaStore {

}