package de.tuda.stg.consys.invariants.subset.model;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.microsoft.z3.*;
import de.tuda.stg.consys.invariants.subset.ClassConstraints;
import de.tuda.stg.consys.invariants.subset.ClassProperties;
import de.tuda.stg.consys.invariants.subset.model.types.TypeModelFactory;
import org.eclipse.jdt.internal.compiler.lookup.ReferenceBinding;
import org.jmlspecs.jml4.ast.JmlTypeDeclaration;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

public class ProgramModel {
	static {
		loadZ3Libs();
	}

	private static void loadLib(Path lib) {
		Path libAbsolute = lib.toAbsolutePath();
		System.out.println("load lib: " + libAbsolute);
		System.load(libAbsolute.toString());
	}

	public static void loadZ3Libs() {
		// Set the correct lib folder
		Path libFolder = Paths.get("consys-invariants","lib");

		// Load the correct libs depending on OS
		String osname = System.getProperty("os.name").toLowerCase();
		if (osname.contains("mac")) {
			loadLib(libFolder.resolve("libz3.dylib"));
			loadLib(libFolder.resolve("libz3java.dylib"));
		} else if (osname.contains("linux")) {
			loadLib(libFolder.resolve("libz3.so"));
			loadLib(libFolder.resolve("libz3java.so"));
		} else {
			throw new RuntimeException("unsupported OS: " + osname);
		}
	}

	public final Context ctx;
	public final Solver solver;

	public final TypeModelFactory types;

	// Stores all class models
	private final Map<ReferenceBinding, ClassModel> models;
	// Stores the sequence in which the models have been added.
	private final List<ReferenceBinding> modelSequence;

	public ProgramModel(Context ctx) {
		this.ctx = ctx;
		this.solver = ctx.mkSolver();
		this.types = new TypeModelFactory(this);

		this.models = Maps.newHashMap();
		this.modelSequence = Lists.newLinkedList();
	}

	public ProgramModel() {
		this(new Context());
	}

	public void addClass(JmlTypeDeclaration jmlClass) {
		ClassModel clazzModel = new ClassModel(this, jmlClass);
		models.put(jmlClass.binding, clazzModel);
		modelSequence.add(jmlClass.binding);
	}

	public void checkAll() {
		for (var binding : modelSequence) {
			// Parse the z3 model from AST.
			ClassModel classModel = models.get(binding);
			ClassConstraints constraints = new ClassConstraints(this, classModel);

			// Check the properties
			ClassProperties properties = new ClassProperties(this, constraints);
			System.out.println("--- Class properties for " + classModel.getClassName());
			System.out.println("initial satisfies invariant: " + properties.checkInitialSatisfiesInvariant());
			System.out.println("initial satisfies mergability: " + properties.checkInitialSatisfiesMergability());
			System.out.println("---");
			System.out.println("merge satisfies invariant: " + properties.checkMergeSatisfiesInvariant());
			System.out.println("merge satisfies mergability: " + properties.checkMergeSatisfiesMergability());
			System.out.println("---");
			classModel.getMethods().forEach(m -> {
				System.out.println("- for method " + m);
				boolean r1 = properties.checkMethodSatisfiesInvariant(m.getBinding());
				System.out.println("  satisfies invariant: " + r1);
				boolean r2 = properties.checkMethodSatisfiesMergability(m.getBinding());
				System.out.println("  satisfies mergability: " + r2);
			});
		}
	}


}
