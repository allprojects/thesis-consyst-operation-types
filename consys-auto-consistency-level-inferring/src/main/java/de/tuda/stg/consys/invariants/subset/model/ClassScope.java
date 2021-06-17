package de.tuda.stg.consys.invariants.subset.model;

import com.google.common.collect.Maps;
import com.microsoft.z3.*;
import org.eclipse.jdt.internal.compiler.ast.AbstractMethodDeclaration;
import org.eclipse.jdt.internal.compiler.ast.FieldDeclaration;
import org.jmlspecs.jml4.ast.JmlMethodDeclaration;
import org.jmlspecs.jml4.ast.JmlTypeDeclaration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ClassScope {

	private final Context ctx;
	// The underlying jml type for this declaration
	private final JmlTypeDeclaration jmlType;

	// Stores all virtual fields of the class
	private final FieldModel[] classFields;
	// Stores all static final fields as constants for usage in formulas
	private final Map<String, ConstantModel> classConstants;

	// Methods
	private final MethodModel[] classMethods;

	// Z3 Sort to represent states of this class.
	private final TupleSort classSort;
	// Z3 functions to access the fields of the tuple
	private final Map<String, FuncDecl> fieldAccessors = Maps.newHashMap();

	private ClassScope(Context ctx, JmlTypeDeclaration jmlType, FieldModel[] classFields, Map<String, ConstantModel> classConstants, MethodModel[] classMethods) {
		this.ctx = ctx;
		this.jmlType = jmlType;
		this.classFields = classFields;
		this.classConstants = classConstants;
		this.classMethods = classMethods;

		// Create a new type for states of this class.
		String[] fieldNames = new String[classFields.length];
		Sort[] fieldSorts =  new Sort[classFields.length];

		for (int i = 0; i < classFields.length; i++) {
			FieldModel field = classFields[i];
			fieldNames[i] = field.getName();
			fieldSorts[i] = field.getSort();
		}
		this.classSort = ctx.mkTupleSort(
				ctx.mkSymbol("state_" + getClassName()),
				Z3Utils.mkSymbols(ctx, fieldNames), fieldSorts);

		FuncDecl<?>[] accessors = classSort.getFieldDecls();
		for (int i = 0; i < classFields.length; i++) {
			fieldAccessors.put(
					classFields[i].getName(),
					accessors[i]
			);
		}

		System.out.println("class model initiated");
	}

	public static ClassScope parseJMLDeclaration(Context ctx, JmlTypeDeclaration jmlType) {

		/* Parse fields and constants */
		List<FieldModel> classFields = new ArrayList(jmlType.fields.length);
		Map<String, ConstantModel> classConstants = Maps.newHashMap();

		for (int i = 0; i < jmlType.fields.length; i++) {
			FieldDeclaration field = jmlType.fields[i];

			//Decide whether field is constant or class field
			if (field.isStatic() && field.binding.isFinal()) {
				// Handle constants
				String fieldName = String.valueOf(field.name);
				Sort sort = Z3Utils.typeReferenceToSort(ctx, field.type);

				if (field.initialization == null)
					throw new IllegalStateException("Constant value must be initialized directly for field " + field);

				ExpressionParser parser = new BaseExpressionParser(ctx);
				Expr initialValue = parser.parseExpression(field.initialization);

				ConstantModel<Sort> constant = new ConstantModel<>(fieldName, sort, initialValue);
				classConstants.put(fieldName, constant);

			} else if (field.isStatic()) {
				// Static fields are not supported
				throw new IllegalStateException("Non-final static fields are unsupported.");
			} else {
				classFields.add(new FieldModel(ctx, field));
			}
		}

		/* Parse methods */
		List<MethodModel> classMethods = new ArrayList(jmlType.methods.length);

		for (int i = 0; i < jmlType.methods.length; i++) {
			AbstractMethodDeclaration method = jmlType.methods[i];

			if (method.isStatic() || method.isAbstract()) {
				throw new IllegalStateException("Static and abstract methods are unsupported.");
			} else if (method instanceof JmlMethodDeclaration) {
				classMethods.add(new MethodModel(ctx, (JmlMethodDeclaration) method));
			} else {
				//TODO: change to sensible defaults.
				throw new IllegalStateException("Only jml method declarations are supported.");
			}
		}

		/* Create the class scope */
		FieldModel[] classFieldsArr = new FieldModel[classFields.size()];
		MethodModel[] classMethodsArr = new MethodModel[classMethods.size()];
		return new ClassScope(ctx, jmlType,
				classFields.toArray(classFieldsArr),
				classConstants,
				classMethods.toArray(classMethodsArr));
	}

	public String getClassName() {
		return String.valueOf(jmlType.name);
	}

	public TupleSort getClassSort() {
		return classSort;
	}

	public FuncDecl<?> getFieldAccessor(String fieldName) {
		FuncDecl<?> result = fieldAccessors.get(fieldName);

		if (result == null) {
			throw new IllegalArgumentException("field <" + fieldName + "> not available");
		}

		return result;
	}

	public Expr getConstantExpr(String constantName) {
		ConstantModel result = classConstants.get(constantName);

		if (result == null) {
			return null;
		}

		return result.getValue();
	}

	public Iterable<MethodModel> getMethods() {
		return Arrays.asList(classMethods);
	}

	public JmlTypeDeclaration getJmlType() {
		return jmlType;
	}
}
