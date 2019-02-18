//package de.tudarmstadt.consistency.storelayer.local.dependency
//
//import de.tudarmstadt.consistency.storelayer.distribution
//import de.tudarmstadt.consistency.storelayer.distribution.SessionService
//import de.tudarmstadt.consistency.storelayer.local.dependency.DepGraph.Op
//import scalax.collection.mutable.Graph
//import scalax.collection.GraphPredef._
//import scalax.collection.GraphEdge._
//import scalax.collection.GraphTraversal.{Parameters, Predecessors}
//
//import scala.collection.mutable
//
//
///**
//	* This is the implementation of a dependency graph between operations.
//	* The graph consists of nodes (id, key, data) which are connected by
//	* directed edges resembling their dependencies.
//	*
//	* Nodes are grouped in transactions, and can be local (= not in sync with
//	* the distributed store).
//	*
//	* @author Mirko Köhler
//	*/
//trait DepGraph[Id, Obj] {
//
//
//	private trait NodeInfo {
//		var local : Boolean
//	}
//
//
//	trait Op{
//		def obj : Obj
//	}
//	case class SetFieldOp(obj : Obj, fldName : String, value : Any) extends Op
//	case class GetFieldOp(obj : Obj, fldName : String) extends Op
//	case class InvokeOp(obj : Obj, mthdName : String, args : Seq[Any]) extends Op
//
//	/* ### Data structures ### */
//	/**
//		* Graph of dependencies between operations. The graph stores identifiers
//		* to operations (as OpRef). These identifiers are looked up in the
//		* operations map.
//		* OpRefs in the graph can be unavailable in the operations map. This means
//		* that the operation has not been resolved from the underlying store
//		* but is known to the graph (e.g. through a dependency of another node).
//		*/
//	private val graph : Graph[Id, DiEdge] = Graph.empty
//
//	/**
//		* Maps ids to their respective operations. Ids that can not be resolved
//		* in the map (i.e. there is no entry), have not been resolved in the
//		* underlying graph, or simply do not exist (although this can not be said for sure).
//		*/
//	private val operations : mutable.Map[Id, Op] = new mutable.HashMap
//
//	/**
//		* Stores the "newest" operation ids for each key. "New" here means that it stores
//		* all operations that no other operation of the same key has a transitive dependency
//		* to that dependency. The concrete conflict resolution algorithm is not
//		* defined here.
//		*/
////	private val keyPointers : mutable.MultiMap[Obj, Id] =
////		new mutable.HashMap[Obj, mutable.Set[Id]] with mutable.MultiMap[Obj, Id]
//
//
//	/* ### Operations ### */
//
//	/**
//		* Adds a new operation to this dependency graph.
//		*
//		* @param id the id of the operation
//		* @param key the key of the operation
//		* @param data the data of the operation
//		* @param tx the transaction id if the operation is part of a transaction
//		* @param deps the dependencies die other operations
//		* @param local true, if the operation should be flagged as a local operation (i.e. if it is not part of the distributed data store and only available locally)
//		*/
//	def addOp(id : Id, key : Obj, data : Data, tx : Option[Txid] = None, deps : Iterable[OpRef] = Iterable.empty, local : Boolean = true) : Unit = {
//		val newRef = store.ref(id, key)
//
//		/*add operation to operations*/
//		operations(id) = OpInfo(key, data, local)
//
//		/*add an entry to the transaction if there is one*/
//		tx.foreach { txid => addOpsToTx(txid, newRef) }
//
//		/*add operation node to dependency graph*/
//		graph.add(newRef)
//		/*add all dependencies to the graph*/
//		deps.foreach(dep => {
//			graph.add(dep ~> newRef)
//		})
//
//		/*update keys*/
//		updatePointersForNewKey(newRef)
//	}
//
//
//	private def updatePointersForNewKey(newRef : OpRef) : Unit = {
//		//The key to be checked
//		val key = newRef.key
//		//the id of the new update
//		val newId = newRef.id
//
//		//obtains the node of the new ref from the graph
//		val newNode = graph.get(newRef)
//		//obtains the current node ids for the key that is checked
//		val pointers = keyPointers.get(key).toSet.flatten
//
//
//		//Check whether to delete entries in the keys map
//		val isPredecessor = pointers.exists({ id =>
//			val r = ref(id, key)
//			val node = graph.get(r)
//
//			if (newNode.isSuccessorOf(node)) {
//				keyPointers.removeBinding(key, id)
//			}
//
//			newNode.isPredecessorOf(node)
//		})
//
//		if (!isPredecessor) {
//			keyPointers.addBinding(key, newId)
//		}
//	}
//
//
//	def +=(id : Id, key : Obj, data : Data, tx : Option[Txid], deps : OpRef*) : Unit = {
//		addOp(id, key, data, tx, deps)
//	}
//
//	def +=(id : Id, key : Obj, data : Data, deps : OpRef*) : Unit = {
//		addOp(id, key, data, deps = deps)
//	}
//
//	/**
//		* Removes an operation from this dependency graph.
//		*
//		* @param id the id of the operation that is removed
//		* @return the operation that was removed, or None if no operation with the id was known.
//		*/
//	def removeOp(id : Id) : Option[Op[Id, Obj, Data]] = {
//		/*unresolve all nodes where the node is a successor, has to be done before op is removed from operations*/
//		operations.remove(id).map(opInfo => Op(id, opInfo.key, opInfo.data))
//	}
//
//	/**
//		* Adds a new dependency to a transaction.
//		*
//		* @param txid the transaction that is appended
//		* @param ops the id of the operation that is added
//		*/
//	def addOpsToTx(txid : Txid, ops : OpRef*) : Unit = {
//		val txRef = ref(txid)
//
//		//automatically adds a node if one of the edge endpoints does not exist
//		ops.foreach { opRef =>
//			graph.add(opRef ~> txRef)
//			graph.add(txRef ~> opRef)
//		}
//	}
//
//	/**
//		* Adds a new transaction to the dependency graph.
//		*
//		* @param txid the identifier of the transaction.
//		*/
//	def addTx(txid : Txid) : Unit = {
//		transactions(txid) = TxInfo()
//	}
//
//	/**
//		* Removes a transaction from the dependency graph. Does not remove
//		* operations in the transaction.
//		*
//		* @param txid id of the removed transaction
//		*/
//	def removeTx(txid : Txid) : Unit = {
//		transactions.remove(txid)
//	}
//
//	/**
//		* Removes a transaction and all operations in that transaction.
//		*/
//	def purgeTx(txid : Txid) : Unit = {
//		transactions.remove(txid)
//
//		val txNode = getNode(txid)
//
//		txNode.diSuccessors.foreach { node => node.value match {
//			case distribution.OpRef(id : Id, _) => operations.remove(id)
//			case _ => sys.error(s"dependency of tx is expected to be opref")
//		}
//		}
//	}
//
//	/**
//		* Computes one (transitive) reference of an operation
//		* that is not resolved.
//		*
//		* @param ref the reference to the operation.
//		* @return Some reference that is not resolved or None if all references are resolved.
//		*/
//	def unresolvedDependencies(ref : Ref) : Set[Ref] = {
//		getNode(ref).outerNodeTraverser(parameters = Parameters(direction = Predecessors)).filter(node => node.value match {
//			case distribution.OpRef(id : Id, _) => !operations.contains(id)
//			case distribution.TxRef(txid : Txid) => !transactions.contains(txid)
//		}).toSet
//	}
//
//
//	def getDependencies(ref : Ref) : Set[Ref] = {
//		require(getInfo(ref).isDefined)
//		graph.get(ref).diPredecessors.map(_.value)
//	}
//
//	def getDependencies(id : Id, key : Obj) : Set[Ref] =
//		getDependencies(ref(id, key))
//
//	def getDependencies(txid : Txid) : Set[Ref] = {
//		getDependencies(ref(txid) : Ref)
//	}
//
//
//	def getOp(id : Id) : Option[Op[Id, Obj, Data]] =
//		operations.get(id).map(opInfo => Op(id, opInfo.key, opInfo.data))
//
//
//	def apply(id : Id) : Op[Id, Obj, Data] = getOp(id) match {
//		case None => throw new NoSuchElementException(s"operation with id $id does not exist")
//		case Some(op) => op
//	}
//
//
//	def read(key : Obj) : Set[Op[Id, Obj, Data]] = {
//		keyPointers.get(key).toSet.flatten.map(id => {
//			val op = operations(id)
//			Op(id, key, op.data)
//		})
//	}
//
//	/*For testing purposes only*/
//	private[dependency] def getGraph : Graph[store.Ref, DiEdge] = graph
//
//
//	/*
//	Private helper methods
//	*/
//	private def getOpInfo(id : Id) : Option[OpInfo] = operations.get(id)
//	private def getTxInfo(txid : Txid) : Option[TxInfo] = transactions.get(txid)
//
//	private def getInfo(ref : Ref) : Option[NodeInfo] = ref match {
//		case distribution.OpRef(id, key) => operations.get(id)
//		case distribution.TxRef(txid) => transactions.get(txid)
//	}
//
//	private def getNode(id : Id, key : Obj) : graph.NodeT =
//		graph.get(ref(id, key))
//	private def getNode(txid : Txid) : graph.NodeT =
//		graph.get(ref(txid))
//	private def getNode(ref : Ref) : graph.NodeT =
//		graph.get(ref)
//}
//
//
//object DepGraph {
//
//	case class Op[Id, Key, Data](id : Id, key : Key, data : Data) extends Product3[Id, Key, Data] {
//		@inline override final def _1 : Id = id
//		@inline override final def _2 : Key = key
//		@inline override final def _3 : Data = data
//	}
//
//}
