package com.theyawns.domain.payments.database;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.theyawns.Constants;
import com.theyawns.domain.payments.Transaction;
import com.theyawns.launcher.BankInABoxProperties;

import java.text.DecimalFormat;

// Uses MapStore to load preauth
public class LazyPreAuthLoader {

    private int totalToLoad;
    private IMap<String, Transaction> preAuthMap;

    private static final DecimalFormat txnFormat      = new DecimalFormat("00000000000000"); // 14 digit


    public void run() {
        HazelcastInstance client = HazelcastClient.newHazelcastClient();
        preAuthMap = client.getMap(Constants.MAP_PREAUTH);
        totalToLoad = BankInABoxProperties.TRANSACTION_COUNT;
        for (int i=0; i<totalToLoad; i++) {
            String key = txnFormat.format(i);
            preAuthMap.get(key);  // Should force lazy load of key
            // once this works, measure speed and decide how many threads we need to run ...
        }
    }

    public static void main(String[] args) {
        LazyPreAuthLoader main = new LazyPreAuthLoader();
        main.run();
    }
}
