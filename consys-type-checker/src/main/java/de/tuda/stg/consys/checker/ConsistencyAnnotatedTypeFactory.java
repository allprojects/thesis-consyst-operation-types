package de.tuda.stg.consys.checker;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.Tree;
import de.tuda.stg.consys.checker.qual.Inconsistent;
import de.tuda.stg.consys.checker.qual.Mixed;
import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.framework.qual.TypeUseLocation;
import org.checkerframework.framework.type.*;
import org.checkerframework.framework.type.treeannotator.ListTreeAnnotator;
import org.checkerframework.framework.type.treeannotator.TreeAnnotator;
import org.checkerframework.framework.type.typeannotator.ListTypeAnnotator;
import org.checkerframework.framework.type.typeannotator.TypeAnnotator;
import org.checkerframework.framework.util.defaults.QualifierDefaults;
import org.checkerframework.javacutil.AnnotationBuilder;
import org.checkerframework.javacutil.TreeUtils;
import org.checkerframework.javacutil.TypesUtils;
import scala.Option;
import scala.Tuple2;
import scala.Tuple4;

import javax.lang.model.element.*;
import java.util.Stack;

public class ConsistencyAnnotatedTypeFactory extends BaseAnnotatedTypeFactory {

	public final InferenceVisitor inferenceVisitor;

	private final Stack<Tuple2<TypeElement, String>> mixedClassContext;

	public ConsistencyAnnotatedTypeFactory(BaseTypeChecker checker) {
        /*
        	Set useFlow to false if the flow analysis should be used.
         */
		super(checker, false);
		if (this.getClass().equals(ConsistencyAnnotatedTypeFactory.class)) {
			this.postInit();
		}

		this.inferenceVisitor = new InferenceVisitor(this);
		this.mixedClassContext = new Stack<>();
	}


	@Override
	protected TreeAnnotator createTreeAnnotator() {
		TreeAnnotator others = super.createTreeAnnotator();
		return new ListTreeAnnotator(others, new ConsistencyTreeAnnotator(this));
	}

	@Override
	protected TypeAnnotator createTypeAnnotator() {
		TypeAnnotator others = super.createTypeAnnotator();
		return new ListTypeAnnotator(others, new ConsistencyTypeAnnotator(this));
	}

	@Override
	protected TypeHierarchy createTypeHierarchy() {
		DefaultTypeHierarchy hierarchy = new DefaultTypeHierarchy(
			checker, getQualifierHierarchy(), checker.getBooleanOption("ignoreRawTypeArguments", true), checker.hasOption("invariantArrays"));

		return new ConsistencyTypeHierarchy(hierarchy, this);
	}

	@Override
	protected QualifierHierarchy createQualifierHierarchy() {
		return new ConsistencyQualifierHierarchy(getSupportedTypeQualifiers(), getElementUtils(), this);
	}

	@Override
	protected void addCheckedCodeDefaults(QualifierDefaults defs) {
		defs.addCheckedCodeDefault(
				AnnotationBuilder.fromClass(getElementUtils(), Inconsistent.class),
				TypeUseLocation.FIELD);

		super.addCheckedCodeDefaults(defs);
	}

	protected void addCheckedCodeDefaultsSkip(QualifierDefaults defs) {
		super.addCheckedCodeDefaults(defs);
	}

	@Override
	protected void addComputedTypeAnnotations(Tree tree, AnnotatedTypeMirror type, boolean iUseFlow) {
		super.addComputedTypeAnnotations(tree, type, iUseFlow);

		switch (tree.getKind()) {
			case CLASS:
				if (TypeFactoryUtils.hasAnnotation(this, ((ClassTree)tree).getModifiers(), TypeFactoryUtils.checkerPackageName() + ".qual.Mixed"))
					inferenceVisitor.visitClass((ClassTree)tree);
				break;

			case IDENTIFIER:
			case VARIABLE:
			case MEMBER_SELECT:
				if (TreeUtils.elementFromTree(tree).getKind() != ElementKind.FIELD)
					return;

				AnnotationMirror mixed = null;
				if (tree.getKind() == Tree.Kind.MEMBER_SELECT) {
					var recvType = getAnnotatedType(((MemberSelectTree)tree).getExpression());
					var classElement = TypesUtils.getTypeElement(recvType.getUnderlyingType());
					var classTree = getTreeUtils().getTree(classElement);

					mixed = recvType.getAnnotation(Mixed.class);
					var defaultOpLevel = (mixed != null) ? TypeFactoryUtils.getMixedDefaultOp(mixed) : "";
					if (classTree != null && mixed != null) {
						pushMixedClassContext(TreeUtils.elementFromDeclaration(classTree), defaultOpLevel);
						inferenceVisitor.visitClass(classTree, new Tuple4<>(Option.empty(), Option.apply(defaultOpLevel), Option.empty(), Option.empty()));
					} else if (mixed != null) {
						pushMixedClassContext(classElement, defaultOpLevel);
						inferenceVisitor.visitClass(classElement, new Tuple4<>(Option.empty(), Option.apply(defaultOpLevel), Option.empty(), Option.empty()));
					}
				}

				if (mixedClassContext.empty())
					return;

				var definedAnnotation = type.getAnnotationInHierarchy(TypeFactoryUtils.inconsistentAnnotation(this));
				var inferredAnnotation = annotateField((VariableElement) TreeUtils.elementFromTree(tree), type);

				if (type.hasExplicitAnnotation(definedAnnotation) && inferenceVisitor.refinementTable().get(tree).isDefined()) {
					var opLevel = inferenceVisitor.refinementTable().get(tree).get();
					type.replaceAnnotation(getQualifierHierarchy().leastUpperBound(opLevel, definedAnnotation));
				} else if (inferredAnnotation != null && inferenceVisitor.refinementTable().get(tree).isDefined()) {
					var opLevel = inferenceVisitor.refinementTable().get(tree).get();
					type.replaceAnnotation(getQualifierHierarchy().leastUpperBound(opLevel, inferredAnnotation));
				} else if (inferredAnnotation != null) {
					type.replaceAnnotation(inferredAnnotation);
				}

				if (mixed != null) {
					popMixedClassContext();
				}
		}
	}

	@Override
	public void addComputedTypeAnnotations(Element elt, AnnotatedTypeMirror type) {
		// When encountering a method invocation on a class that was not yet visited,
		// run the inference first in order to get inferred return type levels
		if (elt.getKind() == ElementKind.METHOD) {
			var classElement = elt.getEnclosingElement();
			var classTree = getTreeUtils().getTree(classElement);
			if (classTree != null && classTree.getKind() == Tree.Kind.CLASS) {
				inferenceVisitor.visitClass((ClassTree)classTree);
			} else if (classElement.getKind() == ElementKind.CLASS) {
				inferenceVisitor.visitClass((TypeElement)classElement);
			}
		}

		super.addComputedTypeAnnotations(elt, type);

		if (elt.getKind() == ElementKind.FIELD) {
			if (type.hasExplicitAnnotation(type.getAnnotationInHierarchy(TypeFactoryUtils.inconsistentAnnotation(this))))
				return;

			var anno = annotateField((VariableElement) elt, type);
			if (anno != null) {
				type.replaceAnnotation(anno);
			}
		}
	}

	private AnnotationMirror annotateField(VariableElement elt, AnnotatedTypeMirror type) {
		if (elt.getSimpleName().toString().equals("this")) // TODO: also do this for "super"?
			return null;
		if (mixedClassContext.empty())
			return null;

		var annotation =
				inferenceVisitor.getInferredFieldOrFromSuperclass(elt, mixedClassContext.peek()._1, mixedClassContext.peek()._2)._1;
		if (annotation.isDefined()) {
			return annotation.get();
		}
		return null;
	}

	public void pushMixedClassContext(TypeElement mixedClassContext, String defaultOpLevel) {
		this.mixedClassContext.push(new Tuple2<>(mixedClassContext, defaultOpLevel));
	}

	public void popMixedClassContext() {
		this.mixedClassContext.pop();
	}

	public void processClassWithoutCache(ClassTree node, String opLevel) {
		shouldCache = false;

		((ConsistencyVisitorImpl)checker.getVisitor()).processMixedClassTree(node, opLevel);

		shouldCache = true;
	}
}
