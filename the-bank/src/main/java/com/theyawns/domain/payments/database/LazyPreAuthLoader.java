package com.theyawns.domain.payments.database;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.core.IMap;
import com.theyawns.Constants;
import com.theyawns.domain.payments.Transaction;
import com.theyawns.launcher.BankInABoxProperties;
import com.theyawns.launcher.Launcher;
import com.theyawns.launcher.RunMode;

import java.io.Serializable;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// PREVIOUSLY: Uses MapStore to load preauth via JDBC
// NOW: bypass MapStore, do load directly so we can fiddle with transaction id
public class LazyPreAuthLoader implements Runnable, Serializable, HazelcastInstanceAware {

    private static final long serialVersionUID = -2306964662183102591L;  // TODO: getting error related to mismatch here

    private HazelcastInstance client;
//    private int totalToLoad;
    private IMap<String, Transaction> preAuthMap;
//    private int offset = 0;
//    private static int numberOfEntries;

    private static final DecimalFormat txnFormat      = new DecimalFormat("00000000000000"); // 14 digit

    @Override
    public void run() {
//        long startTime = System.nanoTime();
//        int startCount = preAuthMap.size();
        int nextTransactionToLoad = 1;
        int chunkSize = BankInABoxProperties.PREAUTH_CHUNK_SIZE;

        TransactionTable table = new TransactionTable();
        //numberOfEntries = table.getTableSize();

        // In Benchmark mode, we process the number of transactions specified in BankInABoxProperties.TRANSACTION_COUNT.
        // In Demo mode, we run until stopped.
        RunMode mode =  Launcher.getRunMode();
        while (true) {
            if (mode == RunMode.Benchmark && nextTransactionToLoad > BankInABoxProperties.TRANSACTION_COUNT) {
                System.out.println("LazyPreAuthLoader, Banchmark mode: Specified transaction count reached, loader stopping");
                break;
            }

            // sleep if we're above HWM.   Doesn't appear this happens ... we're processing nearly as fast as we can load
            if (preAuthMap.size() > BankInABoxProperties.PREAUTH_HIGH_LIMIT) {
                try {
                    System.out.println("preAuth size " + preAuthMap.size() + " above limit " + BankInABoxProperties.PREAUTH_HIGH_LIMIT + ", loader sleeps");
                    Thread.sleep(1000 * BankInABoxProperties.PREAUTH_CHECK_INTERVAL);
                    continue;
                } catch (InterruptedException e) {
                    ;
                }
            }

            // Build an array of keys
            List<String> keys = new ArrayList<>(chunkSize);
            int firstKey = nextTransactionToLoad;
            int lastKey = nextTransactionToLoad + chunkSize - 1;
            for (int i=0; i<chunkSize; i++) {
                String key = txnFormat.format(nextTransactionToLoad++);
                keys.add(key);
            }
            System.out.println("Created chunk of " + keys.size() + " keys (" + firstKey + "-" + lastKey + ")");

            Map<String, Transaction> transactions = table.loadAll(keys);
            //System.out.println("  Loaded " + keys.size() + " keys into Java HashMap resulting in " + transactions.entrySet().size() + " entries");
            for (String key : keys) {
                Transaction t = transactions.get(key);
                if (key == null)
                    System.out.println(" ERROR: Null key");
                else if (t == null)
                    System.out.println(" ERROR: Null entry for key " + key);
                else
                    preAuthMap.put(key, t);
            }
            System.out.println("  " + transactions.size() + " new transactions loaded to IMap, preAuth size now " + preAuthMap.size());

        }
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

//    // not needed in current design ... .
//    private void moveOffset() {
//        offset += BankInABoxProperties.TRANSACTION_COUNT;
//    }
}
