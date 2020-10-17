package com.theyawns.banking.database;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.crdt.pncounter.PNCounter;
import com.hazelcast.map.IMap;
import com.theyawns.controller.Constants;
import com.theyawns.banking.Transaction;
import com.theyawns.controller.launcher.BankInABoxProperties;
import com.theyawns.controller.launcher.RunMode;

import java.io.Serializable;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

// PREVIOUSLY: Uses MapStore to load preauth via JDBC
// NOW: bypass MapStore, do load directly so we can fiddle with transaction id
// This is submitted by Launcher to the DistributedExecutorService for execution
public class LazyPreAuthLoader implements Callable<Exception>, Serializable, HazelcastInstanceAware {

    private static final long serialVersionUID = -2306964662183102591L;  // TODO: getting error related to mismatch here

    private HazelcastInstance hazelcast;
    private RunMode runMode;
    private IMap<String, Transaction> preAuthMap;
    private PNCounter loadedToPreAuth;
    private static final DecimalFormat txnFormat      = new DecimalFormat("00000000000000"); // 14 digit

    private boolean verbose; // not currently using this as I like all the output
    private int chunkSize;
    private int txnCount;
    private int highLimit;
    private int checkIntervalMs;

    // Pass values we used to fetch from BankInABoxProperties; can't be sure version of that
    // file deployed on server matches latest client-side changes.
    public LazyPreAuthLoader(RunMode runMode, int chunkSize, int txnCount, int highLimit, int checkIntervalMs) {
        this.runMode = runMode;
        this.chunkSize = chunkSize;
        this.txnCount = txnCount;
        this.highLimit = highLimit;
        this.checkIntervalMs = checkIntervalMs;
    }

    public void setVerbose(boolean verbose) { this.verbose = verbose; }

    @Override
    public Exception call() {
        try {
            int nextTransactionToLoad = 0;

            TransactionTable table = new TransactionTable();

            // In Benchmark mode, we process the number of transactions specified in BankInABoxProperties.TRANSACTION_COUNT.
            // In Demo mode, we run until stopped.
            //RunMode runMode = Launcher.getRunMode();
            preAuthMap.clear();  // in case IMDG cluster still has previous run's data
            while (true) {
                if (runMode == RunMode.Benchmark && nextTransactionToLoad >= txnCount) {
                    System.out.println("LazyPreAuthLoader, Banchmark mode: Specified transaction count reached, loader stopping");
                    break;
                }
                int alreadyQueued = preAuthMap.size();

                // sleep if we're above HWM.  Doesn't happen in local config but with network latency it does
                if (alreadyQueued >= highLimit) {
                    try {
                        System.out.println("preAuth size " + alreadyQueued + " above limit " + highLimit + ", loader sleeps");
                        Thread.sleep(1000 * checkIntervalMs);
                        continue;
                    } catch (InterruptedException e) {
                        ;
                    }
                }

                // Build an array of keys
                List<String> keys = new ArrayList<>(chunkSize);
                int firstKey = nextTransactionToLoad;
                int lastKey = nextTransactionToLoad + chunkSize - 1;
                for (int i = 0; i < chunkSize; i++) {
                    String key = txnFormat.format(nextTransactionToLoad++);
                    keys.add(key);
                }
                //System.out.println("Created chunk of " + keys.size() + " keys (" + firstKey + "-" + lastKey + ")");

                Map<String, Transaction> transactions = table.loadAll(keys);
                //System.out.println("  Loaded " + keys.size() + " keys into Java HashMap resulting in " + transactions.entrySet().size() + " entries");
                for (String key : keys) {
                    Transaction t = transactions.get(key);
                    //System.out.println("loaded " + t);
                    if (key == null)
                        System.out.println(" ERROR: Null key");
                    else if (t == null)
                        System.out.println(" ERROR: Null entry for key " + key);

                    // TimeEnqueued set by the map listener so in C/S setup we aren't
                    // adding inbound network delay to our reported latency.
                    //else
                    t.setTimeEnqueuedForRuleEngine();
                }
                preAuthMap.putAll(transactions);
                System.out.print("  " + transactions.size() + " new transactions loaded to IMap, preAuth size now " + preAuthMap.size());
                if (runMode == RunMode.Benchmark)
                    System.out.printf("; loaded %d of %d transactions\n", nextTransactionToLoad, txnCount);
                else
                    System.out.println();
                loadedToPreAuth.getAndAdd(transactions.size());
            }
        } catch (Exception e) {
            //IMap emap = hazelcast.getMap("Exceptions");
            //emap.put("AggregationExecutor", e);
            e.printStackTrace();
            return e;
        }
        return null;
    }

    public static void main(String[] args) {
        LazyPreAuthLoader main = new LazyPreAuthLoader(RunMode.Benchmark,
                BankInABoxProperties.PREAUTH_CHUNK_SIZE,
                BankInABoxProperties.TRANSACTION_COUNT,
                BankInABoxProperties.PREAUTH_HIGH_LIMIT,
                BankInABoxProperties.PREAUTH_CHECK_INTERVAL);
        Exception e = main.call();
    }

    @Override
    public void setHazelcastInstance(HazelcastInstance hazelcastInstance) {
        hazelcast = hazelcastInstance;
        preAuthMap = hazelcast.getMap(Constants.MAP_PREAUTH);
        loadedToPreAuth = hazelcast.getPNCounter(Constants.PN_COUNT_LOADED_TO_PREAUTH);
    }
}
