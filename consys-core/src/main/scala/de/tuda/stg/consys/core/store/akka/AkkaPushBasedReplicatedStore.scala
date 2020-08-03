package de.tuda.stg.consys.core.store.akka

import akka.actor.{Actor, ActorRef}
import de.tuda.stg.consys.core.store.akka.AkkaPushBasedReplicatedStore.{Message, PutObjects, StoreEntry}
import scala.collection.mutable

trait AkkaPushBasedReplicatedStore {
	type Key <: java.io.Serializable
	type Value <: java.io.Serializable

	def replicaActor : ActorRef
	def otherReplicas : Iterable[ActorRef]


	/* The objects stored in the local replica */
	private val localObjects : mutable.Map[Key, StoreEntry] = mutable.HashMap.empty

	private def localPutObjects[T <: Value](objects : Map[Key, Value], timestamp : Long = System.currentTimeMillis()) : Unit = {
		objects.foreach(entry => {
			val (key, value) = entry
			localObjects.put(key, StoreEntry(timestamp, value))
		})
	}

	private def broadcast(msg : Message) : Unit =
		otherReplicas.foreach(actorRef => actorRef ! msg)


	def getObject[T <: Value](key : Key) : Option[Value] = {
		localObjects.get(key).map(_.obj.asInstanceOf[Value])
	}

	def putObject[T <: Value](key : Key, obj : Value, timestamp : Long = System.currentTimeMillis()) : Unit = {
		val objects = Map(key -> obj)
		localPutObjects(objects, timestamp)
		broadcast(PutObjects(objects, timestamp))
	}

	def putObjects[T <: Value](objects : Map[Key, Value], timestamp : Long = System.currentTimeMillis()) : Unit = {
		localPutObjects(objects, timestamp)
		broadcast(PutObjects(objects, timestamp))
	}



	/* An entry of the local replica */
	private class ReplicaActor extends Actor {
		override def receive : Receive = {
			case PutObjects(objects, timestamp) => localPutObjects(objects.asInstanceOf[Map[Key, Value]], timestamp)
		}
	}
}

object AkkaPushBasedReplicatedStore {
	type GenKey <: java.io.Serializable
	type GenValue <: java.io.Serializable

	case class StoreEntry(var timeStamp : Long, obj : Any)

	trait Message extends Serializable
	case class PutObjects[K <: GenKey, V <: GenValue](objects : Map[K, V], timestamp : Long) extends Message


	class VectorClock private(val clock : Map[ActorRef, Int]) extends Serializable {
		def this(thisReplica : ActorRef, otherReplicas : Iterable[ActorRef]) = {
			//Fill clock with all replicas initialized to 0
			this(thisReplica, otherReplicas.zip(Iterable.fill(otherReplicas.size)(0)).toMap + (thisReplica -> 0))
		}

		def <=(other : VectorClock) : Boolean = {
			if (other == null) throw new NullPointerException("argument can not be null")
			else if (clock.keySet != other.clock.keySet) throw new IllegalArgumentException("the keysets of the clocks have to equal")
			clock.forall(entry => {
				val (ref, count) = entry
				count <= other.clock.getOrElse(ref, 0)
			})
		}

		/** Tests whether this vector clock is concurrent to another vector clock. */
		def <>(other : VectorClock) : Boolean = {
			!(other <= this) && !(this <= other)
		}

		def inc(ref : ActorRef) : VectorClock = {
			new VectorClock(clock + (ref -> (clock(ref) + 1)))
		}

		def merge(other : VectorClock) : VectorClock = {
			if (clock.keySet != other.clock.keySet) throw new IllegalArgumentException("the keysets of the clocks have to equal")
			val newClock = clock.map(entry => {
				val (ref, count) = entry
				(ref, math.max(count, other.clock(ref)))
			})
			new VectorClock(newClock)
		}

		override def equals(obj : Any) : Boolean = obj match {
			case vc : VectorClock => clock.equals(vc.clock)
			case _ => false
		}
	}
}
