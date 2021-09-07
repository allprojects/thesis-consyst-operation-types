package operations;

import de.tuda.stg.consys.annotations.Transactional;
import de.tuda.stg.consys.annotations.methods.StrongOp;
import de.tuda.stg.consys.checker.qual.Mixed;
import de.tuda.stg.consys.checker.qual.Strong;
import de.tuda.stg.consys.checker.qual.Weak;
import de.tuda.stg.consys.japi.Ref;

import java.io.Serializable;

public class ReturnTypeTest {
    @Transactional
    void test(Ref<A> o) {
        @Strong int a;
        a = o.ref().f();

        // :: error: assignment.type.incompatible
        a = o.ref().g(true);
        @Weak int b;
        b = o.ref().g(true);

        // :: error: assignment.type.incompatible
        b = o.ref().h2();
    }

    static abstract @Mixed class A {
        int i;
        @Weak int j;

        @StrongOp
        int f() {
            i = 0;
            return i;
        }

        int g(boolean b) {
            if (b)
                return i;
            else
                return j;
        }

        void h() { }
        abstract void h1();
        abstract int h2(); // this stays Inconsistent
    }
}
