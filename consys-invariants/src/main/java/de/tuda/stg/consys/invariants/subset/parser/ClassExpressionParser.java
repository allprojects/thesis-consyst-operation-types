package de.tuda.stg.consys.invariants.subset.parser;

import com.microsoft.z3.Expr;
import de.tuda.stg.consys.invariants.exceptions.UnsupportedJMLExpression;
import de.tuda.stg.consys.invariants.subset.model.BaseClassModel;
import de.tuda.stg.consys.invariants.subset.ProgramModel;
import org.eclipse.jdt.internal.compiler.ast.Expression;
import org.eclipse.jdt.internal.compiler.ast.QualifiedNameReference;
import org.eclipse.jdt.internal.compiler.ast.ThisReference;
import org.eclipse.jdt.internal.compiler.lookup.FieldBinding;
import org.jmlspecs.jml4.ast.JmlFieldReference;
import org.jmlspecs.jml4.ast.JmlQualifiedNameReference;
import org.jmlspecs.jml4.ast.JmlSingleNameReference;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Parser for parsing expression inside of classes.
 */
public class ClassExpressionParser extends BaseExpressionParser {

	// The scope of the class in which this expression is parsed. Used to resolve field names.
	private final BaseClassModel classModel;
	// A const definition for substituting this references. The sort has to be the sort of the class.
	private Expr thisConst; // Can be null.

	/**
	 *
	 * @param thisConst Substitute all `this` references with the given const. The const needs to have
	 *                  the same sort as the class that is parsed.
	 */
	public ClassExpressionParser(ProgramModel model, BaseClassModel classModel, Expr thisConst) {
		super(model);

		if (thisConst != null && !thisConst.getSort().equals(classModel.getClassSort()))
			throw new IllegalArgumentException("the sort for `this` has to match the sort of the class");

		this.classModel = classModel;
		this.thisConst = thisConst;
	}


	@Override
	protected Expr parseJmlSingleReference(JmlSingleNameReference jmlSingleNameReference, int depth) {
		Optional<Expr> constantExpr = classModel.getConstant(jmlSingleNameReference)
				.map(cons -> cons.getValue());

		if (constantExpr.isPresent()) {
			return constantExpr.get();
		}

		Optional<Expr> fieldExpr = classModel.getField(jmlSingleNameReference)
				.map(field -> field.getAccessor().apply(thisConst));

		if (fieldExpr.isPresent()) {
			return fieldExpr.get();
		}

		return super.parseJmlSingleReference(jmlSingleNameReference, depth);
	}

	@Override
	protected Expr parseThisReference(ThisReference thisReference, int depth) {
		return thisConst;
	}

//	@Override
//	protected Expr parseJmlFieldReference(JmlFieldReference fieldReference, int depth) {
//		Expr receiver = parseExpression(fieldReference.receiver, depth + 1);
//
//		if (fieldReference.binding.declaringClass.equals(classModel.getBinding())) {
//			return classModel.getField(fieldReference)
//					.map(field -> field.getAccessor().apply(receiver))
//					.orElseThrow(() -> new UnsupportedJMLExpression(fieldReference));
//		}
//
//		return super.parseJmlFieldReference(fieldReference, depth);
//	}

	@Override
	protected Expr parseJmlQualifiedNameReference(JmlQualifiedNameReference jmlQualifiedNameReference, int depth) {

		// check whether a is a field in classModel, i.e., a.b == this.a.b
		if (jmlQualifiedNameReference.binding instanceof FieldBinding) {

			if (!jmlQualifiedNameReference.actualReceiverType.equals(classModel.getBinding())) {
				throw new UnsupportedJMLExpression(jmlQualifiedNameReference, "expected `this` class as receiver");
			}

			var receiverBinding = (FieldBinding) jmlQualifiedNameReference.binding;
			var receiverField = classModel.getField(receiverBinding)
					.orElseThrow(() -> new UnsupportedJMLExpression(jmlQualifiedNameReference, "field not in `this` class"));
			var receiverExpr = receiverField.getAccessor().apply(getThisConst());

			var result = handleQualifiedName(jmlQualifiedNameReference, receiverExpr);

			return result;
		}




		return super.parseJmlQualifiedNameReference(jmlQualifiedNameReference, depth);

	}





	protected <T> T withThisReference(Expr otherConst, Supplier<T> f) {
		Expr prev = this.thisConst;
		thisConst = otherConst;

		T result = null;
		try {
			result = f.get();
		} finally {
			thisConst = prev;
		}

		return result;
	}

	protected Expr getThisConst() {
		return thisConst;
	}

	protected BaseClassModel getClassModel() {
		return classModel;
	}


}
