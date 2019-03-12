package de.tudarmstadt.consistency.checker

import java.util

import com.sun.source.tree._
import javax.lang.model.element.AnnotationMirror
import org.checkerframework.common.basetype.{BaseTypeChecker, BaseTypeVisitor}
import org.checkerframework.framework.`type`.AnnotatedTypeMirror
import org.checkerframework.framework.`type`.AnnotatedTypeMirror.AnnotatedDeclaredType
import org.checkerframework.framework.source.Result

import scala.collection.{JavaConverters, mutable}

/**
	* Created on 05.03.19.
	*
	* @author Mirko Köhler
	*/
class ConsistencyVisitorImpl(checker : BaseTypeChecker) extends BaseTypeVisitor[ConsistencyAnnotatedTypeFactory](checker){
	import TypeFactoryUtils._

	implicit val implicitTypeFactory : ConsistencyAnnotatedTypeFactory = atypeFactory

	//Current context of the consistency check
	private val implicitContext : ImplicitContext = new ImplicitContext


	/*
		Increase implicit context.
	 */
	override def visitIf(node : IfTree, p : Void) : Void = {
		val conditionAnnotation : AnnotationMirror = weakestConsistencyInExpression(node.getCondition)
		implicitContext.set(conditionAnnotation)
		//The condition is executed under the implicit context as well .
		var r : Void = scan(node.getCondition, p)
		r = reduce(scan(node.getThenStatement, p), r)
		r = reduce(scan(node.getElseStatement, p), r)
		implicitContext.reset()
		r
	}

	override def visitWhileLoop(node : WhileLoopTree, p : Void) : Void = {
		val conditionAnnotation : AnnotationMirror = weakestConsistencyInExpression(node.getCondition)
		implicitContext.set(conditionAnnotation)
		var r : Void = scan(node.getCondition, p)
		r = reduce(scan(node.getStatement, p), r)
		implicitContext.reset()
		r
	}

	override def visitDoWhileLoop(node : DoWhileLoopTree, p : Void) : Void = {
		val conditionAnnotation : AnnotationMirror = weakestConsistencyInExpression(node.getCondition)
		implicitContext.set(conditionAnnotation)
		var r : Void = scan(node.getCondition, p)
		r = reduce(scan(node.getStatement, p), r)
		implicitContext.reset()
		r
	}

	override def visitForLoop(node : ForLoopTree, p : Void) : Void = {
		val conditionAnnotation : AnnotationMirror = weakestConsistencyInExpression(node.getCondition)
		var r : Void = scan(node.getInitializer, p)
		r = reduce(scan(node.getCondition, p), r)
		implicitContext.set(conditionAnnotation)
		r = reduce(scan(node.getUpdate, p), r)
		r = reduce(scan(node.getStatement, p), r)
		implicitContext.reset()
		r
	}

	override def visitEnhancedForLoop(node : EnhancedForLoopTree, p : Void) : Void = { //TODO: add variable to implicit context?
		val conditionAnnotation : AnnotationMirror = weakestConsistencyInExpression(node.getExpression)
		var r : Void = scan(node.getVariable, p)
		r = reduce(scan(node.getExpression, p), r)
		implicitContext.set(conditionAnnotation)
		r = reduce(scan(node.getStatement, p), r)
		implicitContext.reset()
		r
	}

	override def visitSwitch(node : SwitchTree, p : Void) : Void = {
		val conditionAnnotation : AnnotationMirror = weakestConsistencyInExpression(node.getExpression)
		var r : Void = scan(node.getExpression, p)
		implicitContext.set(conditionAnnotation)
		r = reduce(scan(node.getCases, p), r)
		implicitContext.reset()
		r
	}


	/*
		Check that implicit contexts are correct.
	 */
	override def visitAssignment(node : AssignmentTree, p : Void) : Void = {
		checkAssignment(atypeFactory.getAnnotatedType(node.getVariable), atypeFactory.getAnnotatedType(node.getExpression), node)

		node.getVariable

		super.visitAssignment(node, p)
	}

	//compound assignment is, e.g., i += 23
	override def visitCompoundAssignment(node : CompoundAssignmentTree, p : Void) : Void = {
		checkAssignment(atypeFactory.getAnnotatedType(node.getVariable), atypeFactory.getAnnotatedType(node.getExpression), node)
		super.visitCompoundAssignment(node, p)
	}


	override def visitVariable(node : VariableTree, p : Void) : Void = {
		val initializer : ExpressionTree = node.getInitializer
		if (initializer != null) checkAssignment(atypeFactory.getAnnotatedType(node), atypeFactory.getAnnotatedType(initializer), node)
		super.visitVariable(node, p)
	}

	private def checkAssignment(lhsType : AnnotatedTypeMirror, rhsType : AnnotatedTypeMirror, tree : Tree) : Unit = {
		if (!implicitContext.allowsUpdatesTo(lhsType, tree) || !implicitContext.allowsUpdatesFrom(rhsType, tree))
			checker.report(Result.failure("assignment.type.implicitflow", lhsType, implicitContext.get, tree), tree)
	}

	override def visitMethodInvocation(node : MethodInvocationTree, p : Void) : Void = {

//		println("method inv " +node)

		if (methodInvocationIsReplicate(node)) {
			println("FOUND SET FIELD")
		}

		node.getMethodSelect match {
			case memberSelectTree : MemberSelectTree =>

				val expr : ExpressionTree = memberSelectTree.getExpression
				val recvType = atypeFactory.getAnnotatedType(expr)



				if (expr != null)
					checkMethodInvocationReceiver(atypeFactory.getAnnotatedType(expr), node)

			case _ =>
		}

		node.getArguments.forEach(argExpr =>
			checkMethodInvocationArgument(atypeFactory.getAnnotatedType(argExpr), node)
		)

		super.visitMethodInvocation(node, p)
	}

	private def methodInvocationIsReplicate(node : MethodInvocationTree) : Boolean = node.getMethodSelect match {
		case memberSelectTree : MemberSelectTree =>
			val expr : ExpressionTree = memberSelectTree.getExpression
			val recvType = atypeFactory.getAnnotatedType(expr)

//			println(s"expr = $expr, recvType = $recvType, method = ${memberSelectTree.getIdentifier}")

			recvType match {
				case adt : AnnotatedDeclaredType if adt.getUnderlyingType.asElement().getSimpleName.toString == "JReplicaSystem" =>
					if (memberSelectTree.getIdentifier.toString == "replicate") {

						val setArg = node.getArguments.get(1)
						val setArgT = atypeFactory.getAnnotatedType(setArg)

						if (!setArgT.getAnnotations.contains(localAnnotation)) {
							println("WARNING: Non-local value replicated")
						}

						val targs = node.getTypeArguments



						println(s"args = ${node.getArguments}, targs = $targs")
					}
				case _ =>
			}

			false

		case _ =>
			false
	}

	private def methodInvocationIsSetField(node : MethodInvocationTree) : Boolean = node.getMethodSelect match {
		case memberSelectTree : MemberSelectTree =>
			val expr : ExpressionTree = memberSelectTree.getExpression
			val recvType = atypeFactory.getAnnotatedType(expr)

			println(s"expr = $expr, recvType = $recvType, method = ${memberSelectTree.getIdentifier}")
			println(recvType.asInstanceOf[AnnotatedDeclaredType].getUnderlyingType.asElement().getSimpleName.toString == "JRef")
			println(memberSelectTree.getIdentifier.toString == "setField")

			recvType match {
				case adt : AnnotatedDeclaredType if adt.getUnderlyingType.asElement().getSimpleName.toString == "JRef" =>
					if (memberSelectTree.getIdentifier.toString == "setField") {
						val setArg = node.getArguments.get(1)

						val setArgT = atypeFactory.getAnnotatedType(setArg)

						val annos = setArgT.getAnnotations

						println(s"args = ${node.getArguments}, argT = $annos")
					}
				case _ =>
			}

			false

		case _ =>
			false
	}

	private def checkMethodInvocationReceiver(receiverType : AnnotatedTypeMirror, tree : Tree) : Unit = {
		if (!implicitContext.allowsAsReceiver(receiverType, tree))
			checker.report(Result.failure("invocation.receiver.implicitflow", receiverType, implicitContext.get, tree), tree)
	}

	private def checkMethodInvocationArgument(argType : AnnotatedTypeMirror, tree : Tree) : Unit = {
		if (!implicitContext.allowsAsArgument(argType, tree))
			checker.report(Result.failure("invocation.argument.implicitflow", argType, implicitContext.get, tree), tree)
	}


	private def weakestConsistencyInExpression(node : ExpressionTree) : AnnotationMirror = {
		/*
				 TODO: This requires an annotated JDK in order to work correctly.

				 With an unannotated JDK we have the following behavior:

				 Definitions:
					 @Strong String s1
					 @Weak String s2
					 public static @PolyConsistent boolean equals(@PolyConsistent Object o1, @PolyConsistent Object o2)

				 s1.equals("hello") --> inconsistent (the normal equals method is always @inconsistent because it is not annotated)
				 equals(s1, "hello") --> strong
				 equals(s1, s2) --> weak
					*/
		//Retrieve the (inferred) annotated type
		getAnnotation(atypeFactory.getAnnotatedType(node))
	}


	private def getAnnotation(`type` : AnnotatedTypeMirror) : AnnotationMirror = { //can only include consistency annotations
		val annotations : util.Set[AnnotationMirror] = `type`.getAnnotations
		if (annotations.size == 1) return annotations.iterator.next
		else if (annotations.isEmpty) return null
		throw new AssertionError("inferred an unexpected number of annotations. Expected 1 annotation, but got: " + annotations)
	}



	private class ImplicitContext {

		private val implicitContexts : mutable.ArrayStack[AnnotationMirror] = new mutable.ArrayStack

		implicitContexts.push(localAnnotation)

		private[checker] def set(annotation : AnnotationMirror) : Unit = {
			val implicitContext : AnnotationMirror = atypeFactory.getQualifierHierarchy.leastUpperBound(annotation, get)
			implicitContexts.push(implicitContext)
		}

		private[checker] def get : AnnotationMirror = implicitContexts.head

		private[checker] def reset() : Unit = {
			implicitContexts.pop
		}

		private def lowerBound(a : AnnotationMirror, b : AnnotationMirror) : AnnotationMirror =
			atypeFactory.getQualifierHierarchy.greatestLowerBound(a, b)

		private def getStrongestNonLocalAnnotationIn(typ : AnnotatedTypeMirror, annotation : AnnotationMirror) : AnnotationMirror = typ match {
			case declaredType : AnnotatedTypeMirror.AnnotatedDeclaredType =>
				var temp : AnnotationMirror = lowerBound(getAnnotation(typ), annotation)

				JavaConverters.iterableAsScalaIterable(declaredType.getTypeArguments).foreach { typeArg =>
					temp = lowerBound(temp, getStrongestNonLocalAnnotationIn(typeArg, temp))
				}

				temp

			case wildcardType : AnnotatedTypeMirror.AnnotatedWildcardType =>
				var temp : AnnotationMirror = lowerBound(getAnnotation(typ), annotation)
				temp = lowerBound(temp, getStrongestNonLocalAnnotationIn(wildcardType.getSuperBound, temp))
				temp = lowerBound(temp, getStrongestNonLocalAnnotationIn(wildcardType.getExtendsBound, temp))
				temp

			case _ =>
				getAnnotation(typ)
		}


		private def canBeAccessed(typ : AnnotatedTypeMirror, tree : Tree) : Boolean = {
			val typeAnnotation : AnnotationMirror = getStrongestNonLocalAnnotationIn(typ, inconsistentAnnotation)

			if (typeAnnotation == null) {
				checker.report(Result.warning("consistency.inferred", typ, tree), tree)
				//Log.info(getClass(), String.format("consistency.inferred: consistency level of {%s} unknown and has been inferred to @Inconsistent.\nin: %s", type, tree));
				return true
			}

			atypeFactory.getQualifierHierarchy.isSubtype(get, typeAnnotation)||
				atypeFactory.getQualifierHierarchy.getBottomAnnotations.contains(typeAnnotation)
		}

		private[checker] def allowsUpdatesTo(typ : AnnotatedTypeMirror, tree : Tree) : Boolean =
			canBeAccessed(typ, tree)

		private[checker] def allowsUpdatesFrom(typ : AnnotatedTypeMirror, tree : Tree) : Boolean =
			canBeAccessed(typ, tree)

		private[checker] def allowsAsReceiver(typ : AnnotatedTypeMirror, tree : Tree) : Boolean =
			canBeAccessed(typ, tree)

		private[checker] def allowsAsArgument(typ : AnnotatedTypeMirror, tree : Tree) : Boolean =
			canBeAccessed(typ, tree)
	}

}