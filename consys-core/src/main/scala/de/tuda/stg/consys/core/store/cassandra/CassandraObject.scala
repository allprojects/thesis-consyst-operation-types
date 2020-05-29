package de.tuda.stg.consys.core.store.cassandra

import de.tuda.stg.consys.core.ConsysUtils
import de.tuda.stg.consys.core.store.{ReflectiveStoredObject, StoreConsistencyLevel, StoredObject}
import jdk.dynalink.linker.support.TypeUtilities

import scala.reflect.ClassTag
import scala.reflect.runtime.universe._


/**
 * Created on 10.12.19.
 *
 * @author Mirko Köhler
 */
private[cassandra] abstract class CassandraObject[T <: java.io.Serializable : TypeTag] extends ReflectiveStoredObject[CassandraStore, T] {
	def addr : CassandraStore#Addr
	def consistencyLevel : StoreConsistencyLevel { type StoreType = CassandraStore }

	def invoke[R](methodId : String, args : Seq[Seq[Any]]) : R = {
		ReflectiveAccess.doInvoke(methodId, args)
	}

	def writeToStore(store : CassandraStore) : Unit

	//This method is called for every object that was part of a transaction.
	//It has to be used to write changes back to Cassandra and to release all locks.
//	def commit() : Unit





}
