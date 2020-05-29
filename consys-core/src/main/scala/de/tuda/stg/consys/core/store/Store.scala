package de.tuda.stg.consys.core.store

import scala.reflect.runtime.universe._
import scala.language.higherKinds

/**
 * Created on 10.12.19.
 *
 * @author Mirko Köhler
 */
trait Store extends AutoCloseable {

	/* The type of addresses in this store */
	type Addr
	/* The type of objects that can be stored */
	type ObjType

	/* The type of transactions */
	type TxContext <: TransactionContext

	/* The type of objects stored */
	type RawType[T <: ObjType] <: StoredObject[_ <: Store, T]
	/* The type to reference stored objects */
	type RefType[T <: ObjType] <: Handler[_ <: Store, T]


	/* A name to distinguish this store */
	def name : String

	/* Starts a new transaction in this store */
	def transaction[T](code : TxContext => Option[T]) : Option[T]

	/* Returns a reference to an object that is stored */
	protected[store] def enref[T <: ObjType : TypeTag](obj : RawType[T]) : RefType[T]
}
