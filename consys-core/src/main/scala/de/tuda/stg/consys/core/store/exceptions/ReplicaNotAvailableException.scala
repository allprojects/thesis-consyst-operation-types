package de.tuda.stg.consys.core.store.exceptions

import de.tuda.stg.consys.core.Address

case class ReplicaNotAvailableException(addr : Address)
	extends Exception(s"the replica $addr cannot be resolved")
