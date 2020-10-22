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

    private static final long serialVersionUID = -2306964662183102591L;

    private HazelcastInstance hazelcast;
    private RunMode runMode;
    private IMap<String, Transaction> preAuthMap;
    private PNCounter loadedToPreAuth;
    private static final DecimalFormat txnFormat      = new DecimalFormat("00000000000000"); // 14 digit

    private boolean verbose; // not currently using this as I like all the output
    private int chunkSize;
    private int txnCount;

    // These used to be configurable, but now with throttling logic should
    // not be necessary.  Giving them some hard coded values as guardrails
    // in case we set throttling limits way too high
    private int highLimit = 100_000; // preAuth can have 100K items waiting
    private int checkIntervalMs = 1000; // if limit hit, recheck every second

    // experimenting with throtting - pass these in new constructor
    // no longer need chunkSize or checkIntervalMs
    private int targetTPS;
    private int chunksPerSecond; // will need to be tuned per environment
                                 // to get average delay to small positive number



    // Pass values we used to fetch from BankInABoxProperties; can't be sure version of that
    // file deployed on server matches latest client-side changes.
    public LazyPreAuthLoader(RunMode runMode, int txnCount, int targetTPS) {
        this.runMode = runMode;
        //this.chunkSize = chunkSize; // calculated with throttling
        this.txnCount = txnCount;
        this.highLimit = highLimit; // not needed with throttling
        this.checkIntervalMs = checkIntervalMs; // not needed with throttling
        // NEW: throttling configurtino
        this.targetTPS = targetTPS;
        // Can make this a property if we find it useful to allow tuning it;
        // for now making it essentially a constant.
        this.chunksPerSecond = 20; // may later add to properties & constructor
        this.chunkSize = targetTPS / chunksPerSecond;
        // secondary throttle - if we see preAuth growth, back off
        this.highLimit = chunkSize * 2;
        this.checkIntervalMs = 500; // check every second whether to resume
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

            // NEW for throttling
            long start = System.currentTimeMillis();
            int msPerBatch = 1000 / chunksPerSecond;
            int batchesSent = 0;
            int timesDelayed = 0;
            int totalDelay = 0;
            int timesBehind = 0;
            int totalBehind = 0;
            int itemsSent = 0;

            while (true) {
                if (runMode == RunMode.Benchmark && nextTransactionToLoad >= txnCount) {
                    System.out.println("LazyPreAuthLoader, Banchmark mode: Specified transaction count reached, loader stopping");

                    // Assess throttling
                    System.out.println("Sent " + batchesSent + " batches of " + chunkSize + " items, total of " + itemsSent + " items");
                    if (timesDelayed > 0)
                        System.out.println("Throttled input " + timesDelayed + " times, average " + (totalDelay / timesDelayed) +  "ms");
                    if (timesBehind > 0)
                        System.out.println("     was behind " + timesBehind  + " times, average " + (totalBehind / timesBehind / chunkSize)+ "ms");

                    break;
                }
                int alreadyQueued = preAuthMap.size();

                // sleep if we're above HWM.  Doesn't happen in local config but with network latency it does
                if (alreadyQueued >= highLimit) {
                    try {
                        System.out.println("preAuth size " + alreadyQueued + " above limit " + highLimit + ", loader sleeps");
                        Thread.sleep(checkIntervalMs);
                        continue;
                    } catch (InterruptedException e) {
                        ;
                    }
                }

                // Build an array of keys
                List<String> keys = new ArrayList<>(chunkSize);
                //int firstKey = nextTransactionToLoad;
                //int lastKey = nextTransactionToLoad + chunkSize - 1;
                for (int i = 0; i < chunkSize; i++) {
                    String key = txnFormat.format(nextTransactionToLoad++);
                    keys.add(key);
                }
                //System.out.println("Created chunk of " + keys.size() + " keys (" + firstKey + "-" + lastKey + ")");

                Map<String, Transaction> transactions = table.loadAll(keys);
                //System.out.println("  Loaded " + keys.size() + " keys into Java HashMap resulting in " + transactions.entrySet().size() + " entries");
                preAuthMap.putAll(transactions);
                System.out.print("  " + transactions.size() + " new transactions loaded to IMap, preAuth size now " + preAuthMap.size());
                if (runMode == RunMode.Benchmark)
                    System.out.printf("; loaded %d of %d transactions\n", nextTransactionToLoad, txnCount);
                else
                    System.out.println();
                loadedToPreAuth.getAndAdd(transactions.size());

                // Throttling
                itemsSent += chunkSize;
                batchesSent++;
                long now = System.currentTimeMillis();
                long nextBatchStart = start + (batchesSent * msPerBatch);
                long delay = nextBatchStart - now;
                //System.out.printf("Start %d now %d next %d delay %d\n", start, now, nextBatchStart, delay);
                if (delay > 0) {
                    timesDelayed++;
                    totalDelay += delay;
                    Thread.sleep(delay);
                } else if (delay < 0) {
                    timesBehind++;
                    totalBehind += delay * -1;
                }
            }
        } catch (Exception e) {
            //IMap emap = hazelcast.getMap("Exceptions");
            //emap.put("AggregationExecutor", e);
            e.printStackTrace();
            return e;
        }
        return null;
    }

    @Override
    public void setHazelcastInstance(HazelcastInstance hazelcastInstance) {
        hazelcast = hazelcastInstance;
        preAuthMap = hazelcast.getMap(Constants.MAP_PREAUTH);
        loadedToPreAuth = hazelcast.getPNCounter(Constants.PN_COUNT_LOADED_TO_PREAUTH);
    }
}
