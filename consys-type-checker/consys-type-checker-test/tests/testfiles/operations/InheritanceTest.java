package testfiles.operations;

import de.tuda.stg.consys.annotations.methods.StrongOp;
import de.tuda.stg.consys.annotations.methods.WeakOp;
import de.tuda.stg.consys.checker.qual.Mixed;
import de.tuda.stg.consys.checker.qual.Strong;
import de.tuda.stg.consys.checker.qual.Weak;

import java.io.Serializable;

// @skip-test

public class InheritanceTest {
    static @Mixed class Base implements Serializable {
        int k;
        int h;

        @WeakOp
        void setK() { k = 0; }

        @StrongOp
        void setH(@Strong int i) {
            h = i;
        }
    }

    static @Mixed class Derived extends Base {
        int j;

        @WeakOp
        void setHDerived() { h = 0; }

        @StrongOp
        void setJfromK() {
            // :: error: assignment.type.incompatible
            j = k;
        }

        @StrongOp
        void setJfromH() {
            // :: error: assignment.type.incompatible
            j = h;
        }
    }
}