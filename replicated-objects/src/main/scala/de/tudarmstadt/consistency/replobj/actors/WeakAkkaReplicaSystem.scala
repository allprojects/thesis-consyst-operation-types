package de.tudarmstadt.consistency.replobj.actors

import akka.actor.{ActorRef, Props}
import de.tudarmstadt.consistency.replobj.ConsistencyLevels
import de.tudarmstadt.consistency.replobj.ConsistencyLevels.{Strong, Weak}
import de.tudarmstadt.consistency.replobj.actors.AkkaReplicaSystem._
import de.tudarmstadt.consistency.replobj.actors.AkkaReplicatedObject._
import de.tudarmstadt.consistency.replobj.actors.WeakAkkaReplicaSystem.WeakReplicatedObject.{WeakFollowerReplicatedObject, WeakMasterReplicatedObject}

import scala.collection.mutable
import scala.language.postfixOps
import scala.reflect.runtime.universe._
import scala.concurrent.duration._


/**
	* Created on 27.02.19.
	*
	* @author Mirko Köhler
	*/

trait WeakAkkaReplicaSystem[Addr] extends AkkaReplicaSystem[Addr] {

	override protected def createMasterReplica[T <: AnyRef : TypeTag, L : TypeTag](addr : Addr, obj : T) : AkkaReplicatedObject[Addr, T, L] = {
		if (ConsistencyLevels.isWeak[L])
		//We have to cast here because the type system can not infer L == Strong
			new WeakMasterReplicatedObject[Addr, T](obj, addr, this).asInstanceOf[AkkaReplicatedObject[Addr, T, L]]
		else
			super.createMasterReplica[T, L](addr, obj)
	}

	override protected def createFollowerReplica[T <: AnyRef : TypeTag, L : TypeTag](addr : Addr, obj : T, masterRef : ActorRef) : AkkaReplicatedObject[Addr, T, L] = {
		if (ConsistencyLevels.isWeak[L])
		//We have to cast here because the type system can not infer L == Strong
			new WeakFollowerReplicatedObject[Addr, T](obj, addr, masterRef, this).asInstanceOf[AkkaReplicatedObject[Addr, T, L]]
		else
			super.createFollowerReplica[T, L](addr, obj, masterRef)
	}
}

object WeakAkkaReplicaSystem {

	trait WeakReplicatedObject[Addr, T <: AnyRef] extends AkkaReplicatedObject[Addr, T, Weak]


	object WeakReplicatedObject {

		class WeakMasterReplicatedObject[Addr, T <: AnyRef](
	     init : T, val addr : Addr, val replicaSystem : AkkaReplicaSystem[Addr]
	   )(
	     protected implicit val ttt : TypeTag[T],
	     protected implicit val ltt : TypeTag[Weak]
	   ) extends WeakReplicatedObject[Addr, T] {

			override val objActor : ActorRef =
				replicaSystem.actorSystem.actorOf(Props(classOf[ObjectActorImpl], this, init, typeTag[T]))


			private class ObjectActorImpl(init : T, protected implicit val objtag : TypeTag[T]) extends ObjectActor {
				setObject(init)

				private val lockQueue : mutable.Queue[(ActorRef, Any)] = mutable.Queue.empty

				override def receive : Receive = {
					case InvokeReq(mthdName, args) =>
						val res = internalInvoke[Any](mthdName, args : _*)
						sender() ! res

					case GetFieldReq(fldName) => //No coordination needed in the get case
						val res = internalGetField[Any](fldName)
						sender() ! res

					case SetFieldReq(fldName, value) =>
						internalSetField(fldName, value)
						sender() ! SetFieldAck

					case SynchronizeWithWeakMaster(ops) =>
						ops.foreach(internalApplyOp[Any])
						sender() ! WeakSynchronized(getObject)
				}
			}

		}

		class WeakFollowerReplicatedObject[Addr, T <: AnyRef](
			init : T, val addr : Addr, val masterReplica : ActorRef, val replicaSystem : AkkaReplicaSystem[Addr]
		)(
			protected implicit val ttt : TypeTag[T],
			protected implicit val ltt : TypeTag[Weak]
		) extends WeakReplicatedObject[Addr, T] {

			override val objActor : ActorRef =
				replicaSystem.actorSystem.actorOf(Props(classOf[ObjectActorImpl], this, init, typeTag[T]))



			private class ObjectActorImpl(init : T, protected implicit val objtag : TypeTag[T]) extends ObjectActor {
				setObject(init)

				/*stores the operations since last synchronize*/
				val unsynchronized : mutable.Buffer[Operation[_]] = mutable.Buffer.empty

				override def receive : Receive = {

					case InvokeReq(mthdName, args) =>
						unsynchronized += InvokeOp(mthdName, args)
						val res = internalInvoke[Any](mthdName, args : _*)
						sender() ! res

					case GetFieldReq(fldName) => //No coordination needed in the get case
						//unsynchronized += GetFieldOp(fldName)
						val res = internalGetField[Any](fldName)
						sender() ! res

					case SetFieldReq(fldName, value) =>
						unsynchronized += SetFieldOp(fldName, value)
						internalSetField(fldName, value)
						sender() ! SetFieldAck

					case SyncReq =>
						val WeakSynchronized(newObj : T) = replicaSystem.request(addr, SynchronizeWithWeakMaster(unsynchronized), masterReplica)
						setObject(newObj)
						unsynchronized.clear()
						sender() ! SyncAck
				}

			}
		}
	}

	private sealed trait WeakReq extends Request
	private case class SynchronizeWithWeakMaster(seq : Seq[Operation[_]]) extends WeakReq with ReturnRequest

	private case class WeakSynchronized[T <: AnyRef](obj : T)

}
