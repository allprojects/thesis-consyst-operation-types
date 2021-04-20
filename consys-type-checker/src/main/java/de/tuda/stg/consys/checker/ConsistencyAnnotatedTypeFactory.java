package de.tuda.stg.consys.checker;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import org.checkerframework.common.basetype.BaseAnnotatedTypeFactory;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.framework.qual.TypeUseLocation;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.DefaultTypeHierarchy;
import org.checkerframework.framework.type.TypeHierarchy;
import org.checkerframework.framework.type.treeannotator.ListTreeAnnotator;
import org.checkerframework.framework.type.treeannotator.TreeAnnotator;
import org.checkerframework.framework.type.typeannotator.ListTypeAnnotator;
import org.checkerframework.framework.type.typeannotator.TypeAnnotator;
import org.checkerframework.framework.util.defaults.QualifierDefaults;
import org.checkerframework.javacutil.AnnotationBuilder;
import org.checkerframework.javacutil.ElementUtils;
import org.checkerframework.javacutil.TreeUtils;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import java.util.Objects;

public class ConsistencyAnnotatedTypeFactory extends BaseAnnotatedTypeFactory {

	private InferenceVisitor inferenceVisitor;

	public ConsistencyAnnotatedTypeFactory(BaseTypeChecker checker) {
        /*
        	Set useFlow to false if the flow analysis should be used.
         */
		super(checker, false);
		if (this.getClass().equals(ConsistencyAnnotatedTypeFactory.class)) {
			this.postInit();
		}

		this.inferenceVisitor = new InferenceVisitor(this);
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
	protected void addCheckedCodeDefaults(QualifierDefaults defs) {
		defs.addCheckedCodeDefault(
				AnnotationBuilder.fromName(getElementUtils(), "de.tuda.stg.consys.checker.qual.Inconsistent"),
				TypeUseLocation.FIELD);

		super.addCheckedCodeDefaults(defs);
	}

	protected void addCheckedCodeDefaultsSkip(QualifierDefaults defs) {
		super.addCheckedCodeDefaults(defs);
	}

	@Override
	protected void addComputedTypeAnnotations(Tree tree, AnnotatedTypeMirror type, boolean iUseFlow) {
		super.addComputedTypeAnnotations(tree, type, iUseFlow);

		if (tree instanceof ClassTree) {
			inferenceVisitor.visitClass((ClassTree)tree, null);
		}

		if ((tree.getKind() == Tree.Kind.IDENTIFIER || tree.getKind() == Tree.Kind.VARIABLE) &&
				TreeUtils.elementFromTree(tree).getKind() == ElementKind.FIELD) {
			annotateField((VariableElement) TreeUtils.elementFromTree(tree), type);
		}
	}

	@Override
	public void addComputedTypeAnnotations(Element elt, AnnotatedTypeMirror type) {
		super.addComputedTypeAnnotations(elt, type);

		if (elt.getKind() == ElementKind.FIELD) {
			annotateField((VariableElement) elt, type);
		}
	}

	private void annotateField(VariableElement elt, AnnotatedTypeMirror type) {
		if (elt.getSimpleName().toString().equals("this"))
			return;
		var fieldName = ElementUtils.getQualifiedName(elt);
		var annotation = inferenceVisitor.fieldTable().apply(fieldName);
		type.clearAnnotations();
		type.addAnnotation(AnnotationBuilder.fromName(getElementUtils(), annotation));
	}

	//    @Override
//    //Trees: check bodies of methods
//    protected void addComputedTypeAnnotations(Tree tree, AnnotatedTypeMirror type, boolean iUseFlow) {
//        super.addComputedTypeAnnotations(tree, type, iUseFlow);
//    }
//
//    @Override
//    //Elements: packages, classes, or methods
//    public void addComputedTypeAnnotations(Element elt, AnnotatedTypeMirror type) {
//        super.addComputedTypeAnnotations(elt, type);
//    }

}
