package de.tuda.stg.consys.core.store.akka

import akka.actor.{Actor, ActorPath, ActorRef, ActorSystem, ExtendedActorSystem, Props, RootActorPath}
import de.tuda.stg.consys.core.Address
import de.tuda.stg.consys.core.store.{DistributedStore, StoreConsistencyLevel}
import de.tuda.stg.consys.core.store.akka.AkkaObject.SyncStrategy
import de.tuda.stg.consys.core.store.akka.AkkaStore.Message
import de.tuda.stg.consys.core.store.akka.AkkaTransactionContext.CachedEntry
import de.tuda.stg.consys.core.store.exceptions.{ObjectNotAvailableException, ReplicaNotAvailableException}
import scala.collection.mutable
import scala.concurrent.{Await, Future, TimeoutException}
import scala.util.{Failure, Success}

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
    private val replicaActor : ActorRef = actorSystem.actorOf(Props(classOf[ReplicaActor], this), DEFAULT_REPLICAACTOR_NAME)

    /* The objects stored in the local replica */
    private val localObjects : mutable.Map[Addr, StoreEntry] = mutable.HashMap.empty

    private val otherActors : Set[ActorRef] = {
      def resolvePath(address : Address) : Option[ActorRef] = {
        val sysname = DEFAULT_ACTORSYSTEM_NAME
        val akkaAddr = akka.actor.Address("akka", sysname, address.hostname, address.port)

        val replicaActorPath = RootActorPath(akkaAddr) / "user" / DEFAULT_REPLICAACTOR_NAME

        //Skip adding the replica if the path is the path to the current replica
        if (replicaActorPath.address.host == getActorSystemAddress.host
          && replicaActorPath.address.port == getActorSystemAddress.port) {
          return None
        }

        val selection = actorSystem.actorSelection(replicaActorPath)

        //Search for the other replica until it is found or the timeout is reached
        val start = System.nanoTime()
        var loop = true
        while (loop) {
          val resolved : Future[ActorRef] = selection.resolveOne(timeout)

          //Wait for resolved to be ready
          Await.ready(selection.resolveOne(timeout), timeout)

          resolved.value match {
            case None =>
              sys.error("Future not ready yet. But we waited for it to be ready. How?")

            case Some(Success(actorRef)) =>
              loop = false
              return Some(actorRef)

            case Some(Failure(exc)) =>
              if (System.nanoTime() > start + timeout.toNanos)
                throw new TimeoutException(s"actor path $replicaActorPath was not resolved in the given time ($timeout).")
          }
        }
        throw new UnsupportedOperationException()
      }

			otherReplicas.map(addr => resolvePath(addr).getOrElse(throw ReplicaNotAvailableException(addr)))
    }


    private[akka] def getActorSystemAddress =
      actorSystem.asInstanceOf[ExtendedActorSystem].provider.getDefaultAddress

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

    def mergeWithLocalState(timestamp : Long, cache : Map[Addr, CachedEntry[_]]) : Unit = {
      for (entry <- cache) {
        //TODO: Implement state merges
        localObjects.put(entry._1, StoreEntry(timestamp, entry._2.obj, entry._2.sync))

      }
    }

    def mergeWithGlobalState(objects : )

    def broadcast(msg : Message) : Unit =
      otherActors.foreach(actorRef => actorRef ! msg)


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

  sealed trait Message
  case class CreateNewReplica[Addr](addr : Addr, obj : Any, level : StoreConsistencyLevel) extends Message
  case class MergeLocalState()
}