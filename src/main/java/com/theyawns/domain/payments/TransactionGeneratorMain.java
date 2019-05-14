package com.theyawns.domain.payments;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.core.HazelcastInstance;

@Deprecated
public class TransactionGeneratorMain {

    public static void main(String[] args) throws InterruptedException {
        HazelcastInstance hazelcast = HazelcastClient.newHazelcastClient();

        TransactionGenerator tgen = new TransactionGenerator();
        tgen.init(hazelcast);
        tgen.start();

    }
}
