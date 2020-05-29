package de.tuda.stg.consys.core.store.exceptions

case class ObjectNotAvailableException(addr : Any)
	extends Exception(s"the object $addr cannot be found on this replica")
