package de.tudarmstadt.consistency.storelayer.distribution

/**
	* Created on 21.12.18.
	*
	* @author Mirko Köhler
	*/
trait IsolationBindings[Isolation] {
	self : SessionService[_, _, _, _, _, Isolation, _] =>

	val Isolation: IsolationOps

	trait IsolationOps {
		def SI : Isolation
		def RC : Isolation
		def RU : Isolation
		def NONE : Isolation
	}
}