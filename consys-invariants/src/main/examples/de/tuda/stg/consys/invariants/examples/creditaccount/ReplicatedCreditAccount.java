package de.tuda.stg.consys.invariants.examples.creditaccount;

import de.tuda.stg.consys.annotations.invariants.ReplicatedModel;
import static de.tuda.stg.consys.utils.InvariantUtils.stateful;

import static de.tuda.stg.consys.utils.InvariantUtils.numOfReplicas;



@ReplicatedModel
public class ReplicatedCreditAccount {

    private final ReplicatedCounter credits;

    /* Invariants */
    //@ public invariant getValue() >= 0;

    //@ ensures getValue() == 0;
    public ReplicatedCreditAccount() {
        credits = new ReplicatedCounter();
    }

    /* Methods */
    //@ assignable \nothing;
    //@ ensures \result == credits.getValue();
    public int getValue() {
        return credits.getValue();
    }


    //@ requires val >= 0;
    //@ assignable credits;
    //@ ensures stateful( credits.increment(val) );
    public void deposit(int val) {
        credits.increment(val);
    }


    //@ requires 0 <= val && val <= getValue();
    //@ assignable credits;
    //@ ensures stateful( credits.increment(val) );
    public void withdraw(int val) {
        if (val > getValue()) throw new IllegalArgumentException();
        credits.increment(val);
    }

    /* Merge method */
    //@ requires (\sum int i; i >= 0 && i < numOfReplicas(); Math.max(credits.values[i], other.credits.values[i])) >= 0;
    //@ ensures stateful( credits.merge(other.credits) );
    public void merge(ReplicatedCreditAccount other) {
        credits.merge(other.credits);
    }
}