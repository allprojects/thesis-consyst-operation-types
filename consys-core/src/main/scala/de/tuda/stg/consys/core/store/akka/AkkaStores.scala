package de.tuda.stg.consys.core.store.akka

import scala.util.DynamicVariable

/**
 * Created on 13.01.20.
 *
 * @author Mirko Köhler
 */
object AkkaStores {
	private[akka] val currentTransaction : DynamicVariable[AkkaTransactionContext] = new DynamicVariable[AkkaTransactionContext](null)

	def getCurrentTransaction : AkkaTransactionContext =
		currentTransaction.value

	def setCurrentTransaction(tx : AkkaTransactionContext) : Unit = currentTransaction synchronized {
		if (currentTransaction.value == null) {
			currentTransaction.value = tx
		} else {
			throw new IllegalStateException(s"unable to set current transaction. transaction already active.\nactive transaction: ${currentTransaction.value}\nnew transaction: $tx")
		}
	}

}
