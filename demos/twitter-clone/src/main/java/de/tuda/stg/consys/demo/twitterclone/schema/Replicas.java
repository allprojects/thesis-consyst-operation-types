package de.tuda.stg.consys.demo.twitterclone.schema;

import de.tuda.stg.consys.japi.JReplicaSystem;
import de.tuda.stg.consys.japi.impl.JReplicaSystems;

public class Replicas {
    @Deprecated
    public static final JReplicaSystem[] replicaSystems = JReplicaSystems.fromActorSystemForTesting(4);
}
