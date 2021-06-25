package de.tuda.stg.consys.invariants.subset.utils;

import com.google.common.collect.Lists;
import com.microsoft.z3.Expr;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;

public class Z3Predicate {
	private final String name;

	private final Expr[] parameters;
	private final Expr body;

	public Z3Predicate(String name, Expr[] parameters, Expr body) {
		this.name = name;
		this.parameters = parameters;
		this.body = body.simplify();
	}

	/**
	 * Substitutes all parameters of the predicate with the given arguments.
	 * If an argument is null, then the parameter will not be substituted.
	 * The list of arguments needs to the same size as the the list of parameters.
	 *
	 * @return An expression where the parameters have been substituted by the arguments.
	 */
	protected Expr apply(Expr... args) {
		if (args.length != parameters.length)
			throw new IllegalArgumentException("args.length != parameters.length");

		List<Expr> _params = Lists.newLinkedList();
		List<Expr> _args = Lists.newLinkedList();
		for (int i = 0; i < args.length; i++) {
			if (args[i] != null) {
				_params.add(parameters[i]);
				_args.add(args[i]);
			}
		}

		return body.substitute(_params.toArray(Expr[]::new), _args.toArray(Expr[]::new));
	}

	@Override
	public String toString() {
		return name + Arrays.toString(parameters) + " = " + body;
	}

}