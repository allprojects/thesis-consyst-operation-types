package de.tuda.stg.consys.checker

import com.sun.source.tree._
import de.tuda.stg.consys.annotations.Transactional
import de.tuda.stg.consys.annotations.methods.{StrongOp, WeakOp}
import de.tuda.stg.consys.checker.qual.Mixed

import javax.lang.model.element.{AnnotationMirror, ElementKind, Modifier, TypeElement}
import org.checkerframework.common.basetype.BaseTypeChecker
import org.checkerframework.dataflow.qual.SideEffectFree
import org.checkerframework.framework.`type`.AnnotatedTypeMirror
import org.checkerframework.framework.`type`.AnnotatedTypeMirror.{AnnotatedDeclaredType, AnnotatedExecutableType}
import org.checkerframework.javacutil.{AnnotationUtils, ElementUtils, TreeUtils, TypesUtils}
import org.jmlspecs.annotation.Pure

import javax.lang.model.`type`.{DeclaredType, NoType, TypeKind}
import scala.collection.convert.ImplicitConversions.`iterable AsScalaIterable`
import scala.collection.convert.ImplicitConversions.`buffer AsJavaList`
import collection.JavaConverters._
import scala.collection.mutable

/**
	* Created on 05.03.19.
	*
	* @author Mirko Köhler
	*/
class ConsistencyVisitorImpl(baseChecker : BaseTypeChecker) extends InformationFlowTypeVisitor[ConsistencyAnnotatedTypeFactory](baseChecker){
	import TypeFactoryUtils._
	type Error = (AnyRef, String, java.util.List[AnyRef])
	private implicit val tf: ConsistencyAnnotatedTypeFactory = atypeFactory
	private val consistencyChecker = baseChecker.asInstanceOf[ConsistencyChecker]

	val classVisitCache: mutable.Map[(String, AnnotationMirror), mutable.Buffer[Error]] = mutable.Map.empty
	val classVisitQueue: mutable.Set[(String, AnnotationMirror)] = mutable.Set.empty

	private var isInConstructor: Boolean = false

	override def processClassTree(classTree: ClassTree): Unit = {
		val className = getQualifiedName(TreeUtils.elementFromDeclaration(classTree))
		var upperBound = atypeFactory.getAnnotatedType(classTree).getAnnotationInHierarchy(inconsistentAnnotation)
		upperBound = repairMixed(upperBound)

		processClassTree(classTree, upperBound)

		getConsistencyQualifiers.
			filter(q => tf.getQualifierHierarchy.isSubtype(q, upperBound) && !AnnotationUtils.areSame(q, upperBound)).
			foreach(a => {
				val q = repairMixed(a)
				consistencyChecker.printErrors = false
				var errors = mutable.Buffer.empty[Error]
				var warnings = mutable.Buffer.empty[Error]
				consistencyChecker.errors = errors.asJava
				consistencyChecker.warnings = warnings.asJava
				processClassTree(classTree, q)
				classVisitCache.put((className, q), errors) // TODO warnings
				consistencyChecker.printErrors = true

				if (classVisitQueue.exists(entry => className == entry._1 && AnnotationUtils.areSame(q, entry._2))) {
					//checker.reportError(classElement, "consistency.type.use.incompatible", getQualifiedName(classElement), getQualifiedName(annotation))
					getErrorsForTypeUse(className, q).
						foreach(entry => checker.reportError(entry._1, entry._2, entry._3.asScala:_*))
				}
			})
	}

	def getErrorsForTypeUse(name: String, q: AnnotationMirror): java.util.List[Error] = {
		val a = repairMixed(q)
		classVisitCache.find(entry => entry._1._1 == name && AnnotationUtils.areSame(entry._1._2, a)) match {
			case None =>
				sys.error("") // TODO: silently fail?
			case Some(value) => value._2
		}
	}

	def visitOrQueueClassTree(classElement: TypeElement, annotation: AnnotationMirror): Unit = {
		val q = repairMixed(annotation)
		val className = getQualifiedName(classElement)
		if (classVisitCache.exists(entry => className == entry._1._1 && AnnotationUtils.areSame(q, entry._1._2))) {
			//checker.reportError(classElement, "consistency.type.use.incompatible", getQualifiedName(classElement), getQualifiedName(annotation))
			// TODO: skip if we already processed these errors
			// TODO: getErrorsForTypeUse does not always find the entry (somehow non deterministic) -> leads to crash
			getErrorsForTypeUse(className, q).
				foreach(entry => checker.reportError(entry._1, entry._2, entry._3.asScala:_*))
		} else if (!classVisitQueue.exists(entry => className == entry._1 && AnnotationUtils.areSame(q, entry._2))) {
			classVisitQueue.add((className, q))
		}
	}

	/**
	 * Visits a class tree under a specific consistency qualifier
	 */
	private def processClassTree(classTree: ClassTree, annotation: AnnotationMirror): Unit = {
		// TODO
		//----------------------------------------------------------------
		// set class context in type factory to upper bound of class tree, then visit class
		// include class context in error output, so that we know which version of the class produces errors
		// in lib mode, also do this for all lower consistencies (w/o cache)
		// if mixed, then run inference before visiting
		// cache visited classes somewhere (name + consistency should be enough)
		// in type factory: - if use of class found somewhere, check cache and visit early if not cached
		// 					- for strong and weak classes: possibly no further action needed, treeannotator already takes care of adaptation
		//                  - for mixed classes: do what we do already

		val classElement = TreeUtils.elementFromDeclaration(classTree)
		val className = getQualifiedName(classElement)

		if (classVisitCache.exists(entry => className == entry._1._1 && AnnotationUtils.areSame(annotation, entry._1._2))){
			return
		} else {
			classVisitCache.put((className, annotation), mutable.Buffer.empty) // TODO warnings
		}
/*
		if (tf.areSameByClass(annotation, classOf[Mixed])) {
			println(s">Class decl: @${annotation.getAnnotationType.asElement().getSimpleName}(${Class.forName(getMixedDefaultOp(annotation)).getSimpleName}) ${getQualifiedName(classTree)}")
		} else {
			println(s">Class decl: @${annotation.getAnnotationType.asElement().getSimpleName} ${getQualifiedName(classTree)}")
		}
*/
		tf.pushVisitClassContext(classElement, annotation)
		if (tf.areSameByClass(annotation, classOf[Mixed])) {
			tf.inferenceVisitor.processClass(classTree, annotation)
		}
		// TODO: how should we handle the cache?
		super.processClassTree(classTree)
		tf.popVisitClassContext()
	}

	/*
		Check that implicit contexts are correct.
	 */
	override def visitAssignment(node : AssignmentTree, p : Void) : Void = {
		//println(s"  >Var assign:\n" +
		//		s"   <$node>\n" +
		//		s"      where ${node.getVariable} -> ${atypeFactory.getAnnotatedType(node.getVariable)}\n" +
		//		s"      where ${node.getExpression} -> ${atypeFactory.getAnnotatedType(node.getExpression)}")

		checkAssignment(atypeFactory.getAnnotatedType(node.getVariable), atypeFactory.getAnnotatedType(node.getExpression), node)
		super.visitAssignment(node, p)
	}

	//compound assignment is, e.g., i += 23
	override def visitCompoundAssignment(node : CompoundAssignmentTree, p : Void) : Void = {
		checkAssignment(atypeFactory.getAnnotatedType(node.getVariable), atypeFactory.getAnnotatedType(node.getExpression), node)
		super.visitCompoundAssignment(node, p)
	}


	override def visitVariable(node : VariableTree, p : Void) : Void = {
		/*
		if (node.getInitializer != null)
			println(s"  >Var decl:\n" +
				s"   ${atypeFactory.getAnnotatedType(node)} ${node.getName}\n" +
				s"   <$node>\n" +
				s"      where ${node.getName} -> ${atypeFactory.getAnnotatedType(node)}\n" +
				s"      where ${node.getInitializer} -> ${atypeFactory.getAnnotatedType(node.getInitializer)}")
		else
			println(s"  >Var decl:\n" +
				s"   ${atypeFactory.getAnnotatedType(node)} ${node.getName}")
		 */

		val initializer: ExpressionTree = node.getInitializer
		if (initializer != null) checkAssignment(atypeFactory.getAnnotatedType(node), atypeFactory.getAnnotatedType(initializer), node)
		super.visitVariable(node, p)
	}

	private def checkAssignment(lhsType : AnnotatedTypeMirror, rhsType : AnnotatedTypeMirror, tree : Tree) : Unit = {
		if (transactionContext && (!implicitContext.allowsUpdatesTo(lhsType, tree)))
			checker.reportError(tree, "assignment.type.implicitflow", lhsType, implicitContext.get, tree)

		tree match {
			case _: VariableTree => // variable initialization at declaration is allowed
			case assign: AssignmentTree => assign.getVariable match {
				case id: IdentifierTree if TreeUtils.elementFromUse(id).getKind != ElementKind.FIELD => // reassigning variables is allowed
				case id: IdentifierTree if isInConstructor && TreeUtils.elementFromUse(id).getKind == ElementKind.FIELD => // allow field initialization in constructor
				case mst: MemberSelectTree => mst.getExpression match {
					case id: IdentifierTree if isInConstructor && TreeUtils.isExplicitThisDereference(id) => // allow field initialization in constructor
					case _ => if (lhsType.hasEffectiveAnnotation(classOf[qual.Immutable]) && !TypesUtils.isPrimitiveOrBoxed(lhsType.getUnderlyingType))
						checker.reportError(tree, "immutability.assignment.type")
				}
				case _ => if (lhsType.hasEffectiveAnnotation(classOf[qual.Immutable]) && !TypesUtils.isPrimitiveOrBoxed(lhsType.getUnderlyingType))
					checker.reportError(tree, "immutability.assignment.type")
			}
			case _ =>
		}
	}

	override def visitMethodInvocation(node : MethodInvocationTree, p : Void) : Void = {
		val prevIsTransactionContext = transactionContext
		if (methodInvocationIsTransaction(node))
			transactionContext = true

		// check transaction violations
		if (!transactionContext) {
			if (methodInvocationIsReplicateOrLookup(node))
				checker.reportError(node, "invocation.replicate.transaction", node)
			if (methodInvocationIsRefAccess(node))
				checker.reportError(node, "invocation.ref.transaction", node)
			if (methodInvocationIsTransactional(node))
				checker.reportError(node, "invocation.method.transaction", node)
		}

		// TODO: also perform checks for implicit this
		node.getMethodSelect match {
			case memberSelectTree : MemberSelectTree =>
				val expr : ExpressionTree = memberSelectTree.getExpression
				val recvType = atypeFactory.getAnnotatedType(expr)
				val methodType = atypeFactory.getAnnotatedType(TreeUtils.elementFromUse(node))

				// check receiver w.r.t. implicit context
				if (expr != null && !methodInvocationIsRefOrGetField(node)) {
					if (recvType.hasEffectiveAnnotation(classOf[Mixed]))
						checkMethodInvocationOpLevel(recvType, node)
					else
						checkMethodInvocationReceiver(recvType, node)
				}

				// check immutability on receiver
				if (!methodInvocationIsRefAccess(node) &&
					!methodInvocationIsReplicateOrLookup(node) &&
					!methodType.getElement.getModifiers.contains(Modifier.STATIC)) {

					checkMethodInvocationReceiverMutability(recvType, methodType, node)
				}

			case _ =>
		}

		// check arguments w.r.t. implicit context
		val methodType = atypeFactory.getAnnotatedType(TreeUtils.elementFromUse(node))
		(methodType.getParameterTypes zip node.getArguments).foreach(entry => {
			val (paramType, argExpr) = entry
			// arguments taken as immutable parameters cannot violate implicit context
			if (!paramType.hasAnnotation(immutableAnnotation))
				checkMethodInvocationArgument(atypeFactory.getAnnotatedType(argExpr), node)
		})

		val r = super.visitMethodInvocation(node, p)

		if (methodInvocationIsTransaction(node))
			transactionContext = prevIsTransactionContext
		r
	}

	override def visitMethod(node: MethodTree, p: Void): Void = {
		var shouldClose = false
		val prevIsTransactionContext = transactionContext
		if (!transactionContext && methodDeclarationIsTransactional(node)) {
			transactionContext = true
			shouldClose = true
		}

		val prevIsConstructor = isInConstructor
		if (TreeUtils.isConstructor(node)) {
			isInConstructor = true
		}

		// check operation level override rules
		if (tf.isInMixedClassContext && !(hasAnnotation(node.getModifiers, classOf[SideEffectFree]) ||
			hasAnnotation(node.getModifiers, classOf[Pure]))) {

			val overrides = ElementUtils.getOverriddenMethods(TreeUtils.elementFromDeclaration(node), tf.types)
			overrides.foreach(m => {
				// TODO: make more general
				if (hasAnnotation(m, classOf[WeakOp]) && !hasAnnotation(node.getModifiers, classOf[WeakOp]))
					checker.reportError(node, "mixed.inheritance.operation.incompatible",
						if (hasAnnotation(node.getModifiers, classOf[StrongOp])) "StrongOp" else "Default",
						"WeakOp", m.getReceiverType)
				else if (!hasAnnotation(m, classOf[StrongOp]) && !hasAnnotation(m, classOf[WeakOp]) &&
					hasAnnotation(node.getModifiers, classOf[StrongOp]))
					checker.reportError(node, "mixed.inheritance.operation.incompatible",
						"StrongOp", "Default", m.getReceiverType)
			})
		}

		// check mutable on return type
		if (!AnnotationUtils.areSame(tf.peekVisitClassContext()._2, inconsistentAnnotation)) {
			val mods = TreeUtils.elementFromDeclaration(node).getModifiers
			val annotatedReturnType = tf.getAnnotatedType(node).getReturnType
			val returnType = annotatedReturnType.getUnderlyingType
			if (!(TreeUtils.isConstructor(node) ||
				returnType.getKind == TypeKind.VOID ||
				TypesUtils.isPrimitiveOrBoxed(returnType) ||
				mods.contains(Modifier.STATIC) ||
				mods.contains(Modifier.PRIVATE) ||
				mods.contains(Modifier.PROTECTED)) &&
				annotatedReturnType.hasEffectiveAnnotation(mutableAnnotation))
			{
				checker.reportError(node.getReturnType, "immutability.return.type")
			}
		}

		val r = super.visitMethod(node, p)

		if (TreeUtils.isConstructor(node))
			isInConstructor = prevIsConstructor
		if (shouldClose)
			transactionContext = prevIsTransactionContext
		r
	}

	private def replicateIsAllowedForLevel(node: MethodInvocationTree): (Boolean, Object) = {
		/*
		// match 'classType' in 'ctx.replicate(_, _, Class<classType>)'
		val argType = atypeFactory.getAnnotatedType(node.getArguments.get(2))
		argType match {
			case adt: AnnotatedDeclaredType => adt.getTypeArguments.get(0) match {
				case classType: AnnotatedDeclaredType =>
					val qualifierName = AnnotationUtils.annotationName(classType.getAnnotationInHierarchy(getTopAnnotation))

					subCheckerMap.get(qualifierName) match {
						case Some(subChecker) =>
							val subCheckerTypeFactory: SubConsistencyAnnotatedTypeFactory = checker.getTypeFactoryOfSubchecker(subChecker)
							// having no sub checker means we are currently in a sub checker so we don't need to test replicate
							if (subCheckerTypeFactory == null)
								(true, null)
							else
								(subCheckerTypeFactory.isAllowed(classType.getUnderlyingType),
									subCheckerTypeFactory.getSrcForDisallowed(classType.getUnderlyingType))
						case None => (true, null)
					}
				}
				case _ => (true, null)
			case _ => (true, null)
		}

		 */
		null
	}

	private def methodInvocationIsX(node: MethodInvocationTree, receiverName: String, methodNames: List[String]) : Boolean = {
		def checkMethodName(memberSelectTree: MemberSelectTree): Boolean = {
			val methodId = memberSelectTree.getIdentifier.toString
			methodNames.map(x => x == methodId).fold(false)(_ || _)
		}
		def checkReceiverNameInInterfaces(dt: DeclaredType, mst: MemberSelectTree): Boolean = dt.asElement() match {
			case te: TypeElement => te.getInterfaces.exists {
				case interfaceType: DeclaredType if getQualifiedName(interfaceType) == receiverName =>
					checkMethodName(mst)
				case interfaceType: DeclaredType =>
					checkReceiverNameInInterfaces(interfaceType, mst)
				case _ => false
			}
			case _ => false
		}
		def checkReceiverNameInSuperClass(dt: DeclaredType, mst: MemberSelectTree): Boolean = dt.asElement() match {
			case te: TypeElement => te.getSuperclass match {
				case _: NoType => false
				case dt: DeclaredType if getQualifiedName(dt) == receiverName =>
					checkMethodName(mst)
				case dt: DeclaredType =>
					checkReceiverNameInInterfaces(dt, mst) || checkReceiverNameInSuperClass(dt, mst)
				case _ => false
			}
			case _ => false
		}

		node.getMethodSelect match {
			case memberSelectTree : MemberSelectTree =>
				val receiverType = atypeFactory.getAnnotatedType(memberSelectTree.getExpression)
				receiverType match {
					// check for a direct name match
					case adt : AnnotatedDeclaredType if getQualifiedName(adt) == receiverName =>
						checkMethodName(memberSelectTree)
					// check for name match in interfaces or superclass
					case adt: AnnotatedDeclaredType =>
						checkReceiverNameInInterfaces(adt.getUnderlyingType, memberSelectTree) ||
							checkReceiverNameInSuperClass(adt.getUnderlyingType, memberSelectTree)
					case _ => false
				}
			case _ => false
		}
	}

	private def methodInvocationIsRefOrGetField(node: MethodInvocationTree): Boolean =
		methodInvocationIsX(node, s"$japiPackageName.Ref", List("ref", "getField"))

	def methodInvocationIsRefAccess(node: MethodInvocationTree): Boolean =
		methodInvocationIsX(node, s"$japiPackageName.Ref", List("ref", "getField", "setField", "invoke"))

	private def methodInvocationIsReplicateOrLookup(node: MethodInvocationTree): Boolean =
		methodInvocationIsX(node, s"$japiPackageName.TransactionContext", List("replicate", "lookup"))

	private def methodInvocationIsReplicate(node: MethodInvocationTree): Boolean =
		methodInvocationIsX(node, s"$japiPackageName.TransactionContext", List("replicate"))

	private def methodInvocationIsTransaction(node: MethodInvocationTree): Boolean =
		methodInvocationIsX(node, s"$japiPackageName.Store", List("transaction"))

	private def methodInvocationIsRefFieldAccess(node: MethodInvocationTree): Boolean =
		methodInvocationIsX(node, s"$japiPackageName.Ref", List("setField", "getField"))

	private def methodDeclarationIsTransactional(node: MethodTree) : Boolean = {
		val annotations = node.getModifiers.getAnnotations
		annotations.exists((at: AnnotationTree) => atypeFactory.getAnnotatedType(at.getAnnotationType) match {
			case adt: AnnotatedDeclaredType =>
				getQualifiedName(adt) == s"$annoPackageName.Transactional"
			case _ =>
				false
		})
	}

	private def methodInvocationIsTransactional(node: MethodInvocationTree) : Boolean = {
		// get the correct method declaration for this invocation and check for annotation
		val execElem = TreeUtils.elementFromUse(node)
		null != atypeFactory.getDeclAnnotation(execElem, classOf[Transactional])
	}

	private def checkMethodInvocationReceiver(receiverType : AnnotatedTypeMirror, tree : Tree) : Unit = {
		if (transactionContext && !implicitContext.allowsAsReceiver(receiverType, tree))
			checker.reportError(tree, "invocation.receiver.implicitflow", receiverType, implicitContext.get, tree)
	}

	private def checkMethodInvocationReceiverMutability(receiverType : AnnotatedTypeMirror, methodType: AnnotatedExecutableType, tree : MethodInvocationTree) : Unit = {
		if (!(transactionContext ||
			!tf.isVisitClassContextEmpty && !AnnotationUtils.areSame(tf.peekVisitClassContext()._2, inconsistentAnnotation)))
			return

		if (receiverType.hasEffectiveAnnotation(classOf[qual.Immutable]) &&
			!(ElementUtils.hasAnnotation(methodType.getElement, classOf[SideEffectFree].getName) ||
				ElementUtils.hasAnnotation(methodType.getElement, classOf[Pure].getName)))
			checker.reportError(tree, "immutability.invocation.receiver")
	}

	private def checkMethodInvocationArgument(argType : AnnotatedTypeMirror, tree : Tree) : Unit = {
		if (transactionContext && !implicitContext.allowsAsArgument(argType, tree))
			checker.reportError(tree, "invocation.argument.implicitflow", argType, implicitContext.get, tree)
	}

	private def checkMethodInvocationOpLevel(recvType: AnnotatedTypeMirror, tree: MethodInvocationTree): Unit = {
		if (transactionContext && recvType.hasEffectiveAnnotation(classOf[Mixed]) && !implicitContext.allowsAsMixedInvocation(recvType, tree))
			checker.reportError(tree, "invocation.operation.implicitflow",
				getMixedOpForMethod(TreeUtils.elementFromUse(tree), getMixedDefaultOp(recvType.getEffectiveAnnotation(classOf[Mixed]))),
				implicitContext.get, tree)
	}



	override protected def getAnnotation(typ : AnnotatedTypeMirror) : AnnotationMirror = {
		typ.getEffectiveAnnotationInHierarchy(getTopAnnotation)
	}

	override protected def getEmptyContextAnnotation : AnnotationMirror = localAnnotation(atypeFactory)

	override protected def getTopAnnotation : AnnotationMirror = inconsistentAnnotation(atypeFactory)

	// TODO: this is a hack to circumvent a possible bug in the checkerframework, where type arguments with multiple
	//		 annotations get erased and can't be inferred. If we remove this, ref() calls crash the checker
	override def skipReceiverSubtypeCheck(node: MethodInvocationTree, methodDefinitionReceiver: AnnotatedTypeMirror, methodCallReceiver: AnnotatedTypeMirror): Boolean =
		true
}
