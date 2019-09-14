package com.theyawns.domain.payments.database;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.core.IMap;
import com.theyawns.Constants;
import com.theyawns.domain.payments.Transaction;
import com.theyawns.launcher.BankInABoxProperties;

import java.io.Serializable;
import java.text.DecimalFormat;

// Uses MapStore to load preauth via JDBC
public class LazyPreAuthLoader implements Runnable, Serializable, HazelcastInstanceAware {

    private HazelcastInstance client;
    private int totalToLoad;
    private IMap<String, Transaction> preAuthMap;

    private static final DecimalFormat txnFormat      = new DecimalFormat("00000000000000"); // 14 digit

    @Override
    public void run() {
        long start = System.nanoTime();
        totalToLoad = BankInABoxProperties.TRANSACTION_COUNT;
        for (int i=0; i<totalToLoad; i++) {
            String key = txnFormat.format(i);
            preAuthMap.get(key);  // Should force lazy load of key
            // once this works, measure speed and decide how many threads we need to run ...
        }
        long finish = System.nanoTime();
        long elapsedMillis = (finish - start) / 1000000;
        System.out.println("Loaded " + totalToLoad + " transactions in " + elapsedMillis + " ms");
        //client.shutdown();   No; this "client" code is running on a member
    }

    public static void main(String[] args) {
        LazyPreAuthLoader main = new LazyPreAuthLoader();
        main.run();
    }

    @Override
    public void setHazelcastInstance(HazelcastInstance hazelcastInstance) {
        client = hazelcastInstance;
        preAuthMap = client.getMap(Constants.MAP_PREAUTH);
    }
}
