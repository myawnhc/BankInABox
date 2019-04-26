package com.theyawns.domain.payments;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.theyawns.listeners.TransactionMapListener;

// Might move this to a domain package (FraudMain, ECommerceMain, etc.)
// May have a Factory for the rule engine so IMDG, Jet, or Both is configurable
@Deprecated
public class IMDGMain {

    private HazelcastInstance hazelcast;
    private IMap<String, Transaction> preAuthMap;

    public void process() {
        hazelcast = HazelcastClient.newHazelcastClient();  // TODO: config as needed
        preAuthMap = hazelcast.getMap("preAuth");
        preAuthMap.addEntryListener(new TransactionMapListener(hazelcast), true);
    }

    public static void main(String[] args) throws InterruptedException {
        IMDGMain main = new IMDGMain();
        main.process();
    }
}
