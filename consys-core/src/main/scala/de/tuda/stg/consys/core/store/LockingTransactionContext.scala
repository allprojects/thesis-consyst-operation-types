package de.tuda.stg.consys.core.store

import de.tuda.stg.consys.core.store.LockingTransactionContext.DistributedLock

import scala.collection.mutable

/**
 * Created on 16.01.20.
 *
 * @author Mirko Köhler
 */
trait LockingTransactionContext extends TransactionContext {

	type StoreType <: Store with LockingStore

	private val acquiredLocks : mutable.Map[StoreType#Addr, StoreType#LockType] = mutable.HashMap.empty

	def acquireLock(addr : StoreType#Addr) : Unit = {
		if (!acquiredLocks.contains(addr)) {
			println(s"try lock $addr")
			val lock : StoreType#LockType = store.retrieveLockFor(addr.asInstanceOf[store.Addr] /* TODO: Why do we need this type cast? */)
			lock.acquire()
			acquiredLocks.put(addr, lock)
			println(s"locked $addr")
		}
	}

	def releaseLock(addr : StoreType#Addr) : Unit = acquiredLocks.get(addr) match {
		case None =>
		case Some(lock) =>
			println(s"released $addr")
			lock.release()
			acquiredLocks.remove(addr)
	}
}

object LockingTransactionContext {
	trait DistributedLock {
		def acquire() : Unit
		def release() : Unit
	}
}