package de.tuda.stg.consys.core.demo

import akka.dispatch.ExecutionContexts
import de.tuda.stg.consys.core.store.legacy.ConsistencyLabel.Strong
import de.tuda.stg.consys.core.store.legacy.akka.AkkaReplicaSystemFactory
import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext

/**
 * Created on 02.03.20.
 *
 * @author Mirko Köhler
 */
object LegacyErrorDemo extends App {

	case class SetWrapper[T](var s: Set[T]) {
		def add(elem: T): Unit = {
			s = s + elem
		}
	}

	implicit val executionContext : ExecutionContext = ExecutionContexts.fromExecutorService(Executors.newFixedThreadPool(12))


	AkkaReplicaSystemFactory.spawn("test/consys0.conf") { system =>
		import system.{Ref, println}

		val ref : Ref[SetWrapper[Int]] = system.replicate("a", SetWrapper(Set(1,2,3)), Strong)
		//    val ref = system.replicate[SetWrapper[Int]]("a", SetWrapper(Set(1,2,3)), Strong)  // Also causes the "type T is not a class" error

		Thread.sleep(500)
		ref.invoke("add", Seq(Seq(4))) //scala.ScalaReflectionException: type T is not a class
		Thread.sleep(500)
		//    ref.invoke[Unit]("add", Seq(Seq(5))) // Same error as ref.invoke(....)
		Thread.sleep(500)
		println(s"ref.s = ${ref("s")}")
		Thread.sleep(5000)
	}


	AkkaReplicaSystemFactory.spawn("test/consys1.conf") { system =>


		/*val ref = system.lookup[SetWrapper[Int]]("a", Strong)
		Thread.sleep(500)
		ref.invoke[Unit]("add", Seq(Seq(6)))
		Thread.sleep(500)
		ref.invoke[Unit]("add", Seq(Seq(7)))*/
		Thread.sleep(5000)
	}

	AkkaReplicaSystemFactory.spawn("test/consys2.conf") { system =>
		import system.println

		val ref = system.lookup[SetWrapper[Int]]("a", Strong)
		println(s"ref.s = ${ref("s")}")
		Thread.sleep(500)
		println(s"ref.s = ${ref("s")}")
		Thread.sleep(500)
		println(s"ref.s = ${ref("s")}")
		Thread.sleep(5000)
	}
}
