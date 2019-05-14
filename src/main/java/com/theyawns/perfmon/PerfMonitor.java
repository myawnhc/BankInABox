package com.theyawns.perfmon;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IAtomicLong;
import com.hazelcast.core.IMap;
import com.hazelcast.core.ISemaphore;
import com.hazelcast.cp.CPSubsystem;
import com.hazelcast.crdt.pncounter.PNCounter;
import com.hazelcast.scheduledexecutor.IScheduledExecutorService;
import com.theyawns.domain.payments.Transaction;

import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class PerfMonitor implements Runnable {

    // TODO: move to properties file and allow command-line override
    private static final int TPS_INTERVAL_MS = 10000;
    private static final int HISTO_INTERVAL_MS = 30000;

    private PNCounter jetCompletionsThisInterval;
    private PNCounter imdgCompletionsThisInterval;
    private PNCounter jetTotalCompletions;
    private PNCounter imdgTotalCompletions;

    // Only one node should be running the timed events.
    private ISemaphore collectSemaphore;
    private ISemaphore outputSemaphore;

    private IAtomicLong jetTotalProcessingTime;
    private IAtomicLong jetTotalEndToEndTime;
    private IAtomicLong imdgTotalProcessingTime;
    private IAtomicLong imdgTotalEndToEndTime;

    private IMap<TransactionKey, Long> operationsInProcess;
    private IMap<BucketKey, Integer> latencies;

    private IScheduledExecutorService collectExecSvc;
    private IScheduledExecutorService outputExecSvc;


    private static class PerfMonitorHolder {
        public static final PerfMonitor instance = new PerfMonitor();
    }

    private PerfMonitor() { init(); }

    public static PerfMonitor getInstance() {
        return PerfMonitorHolder.instance;
    }

    public enum Platform {
        Jet, IMDG,
        // Either is used to start a measurement when we don't know which platform will
        // process it; we will match measurement end for either platform
        Either;
        // Could add a 'both' for split workloads .. stage isn't complete until both
        // platforms report completion
    }

    public enum Scope {
        Processing, EndToEnd;
        // Possible future others: Pipeline stage, EntryProcessor, etc.
    }

    // TODO: IdentifiedDataSerializable
    public static class GroupKey implements Serializable {

        private Platform platform;
        private String operation;  // Rule name, etc
        private Scope scope;

        public GroupKey(Platform platform, Scope scope, String operation) {
            this.platform = platform;
            this.scope = scope;
            this.operation = operation;
        }
        public String toString() {
            return platform + " " + scope + " " + operation + " ";
        }
    }

    public static class BucketKey implements Serializable {
        private GroupKey group;
        private int bucket;
        public BucketKey(GroupKey group, int bucket) {
            this.group = group;
            this.bucket = bucket;
        }
        public String toString() {
            return group.toString() + " " + bucket;
        }
    }

    // TODO: IdentifiedDataSerializable
    public static class TransactionKey implements Serializable {
        private GroupKey group;
        private String id;
        public TransactionKey(Platform platform, Scope scope, String operation, String txnid) {
            this(new GroupKey(platform, scope, operation), txnid);
        }
        public TransactionKey(GroupKey group, String transactionId) {
            this.group = group;
            this.id = transactionId;
        }
        public GroupKey getGroupKey() { return group; }
        public String toString() {
            return group.toString() + " " + id;
        }
        public TransactionKey platformGenericForm() {
            return new TransactionKey(Platform.Either, group.scope, group.operation, id);
        }
    }

    public void beginLatencyMeasurement(Platform platform, Scope scope, String operation, String id) {
        TransactionKey key = new TransactionKey(platform, scope, operation, id);
        beginLatencyMeasurement(key);
    }

    public void beginLatencyMeasurement(TransactionKey key) {
        System.out.println("PerfMon.beginLM: " + key);
        operationsInProcess.set(key, System.currentTimeMillis());
    }

    public void endLatencyMeasurement(Platform platform, Scope scope, String operation, String id) {
        TransactionKey key = new TransactionKey(platform, scope, operation, id);
        endLatencyMeasurement(key);

    }

    public void endLatencyMeasurement(TransactionKey key) {
        System.out.println("PerfMon.endLM:   " + key);
        Long start = operationsInProcess.remove(key); // Slow operation
        if (start == null) {
            start = operationsInProcess.remove(key.platformGenericForm());
            if (start == null) {
                System.out.println("PerfMon.endLM: No measurement started for " + key);
                return;
            }
        }
        long stop = System.currentTimeMillis();
        long elapsed = stop - start;

        GroupKey group = key.getGroupKey();
        BucketKey bucket;
        if (elapsed <= 0) bucket = new BucketKey(group, 0);
        else if (elapsed >= 100) bucket = new BucketKey(group, 101);
        else bucket = new BucketKey(group, (int) elapsed);

        Integer previous = latencies.get(bucket);
        if (previous == null)
            latencies.set(bucket, 0);
        else
            latencies.set(bucket, ++previous);

        // Accumulate time totals for end-of-run averages
//        if (group.platform == Platform.IMDG) {
//            if (group.scope == Scope.Processing) {
//                imdgTotalProcessingTime.getAndAdd(elapsed);
//            } else {
//                imdgTotalEndToEndTime.getAndAdd(elapsed);
//            }
//        } else if (group.platform == Platform.Jet) {
//            if (group.scope == Scope.Processing) {
//                jetTotalProcessingTime.getAndAdd(elapsed);
//            } else {
//                jetTotalEndToEndTime.getAndAdd(elapsed);
//            }
//        }
    }

    /* Right now this is just measuring a single transaction, and comparing the Jet and
     * IMDG implementations.  A more robust version could take transaction name as part of the
     * key and accumulate TPS / latency info for many different transactions; that would require
     * more buckets for the results and reworking the logic but I think that's the direction this
     * will eventually take.
     */
    public void recordTransaction(String key, Transaction t) {
        if (key.equals("Jet"))
            jetCompletionsThisInterval.getAndIncrement();
        else
            imdgCompletionsThisInterval.getAndIncrement();
    }

    public void drawProcessingHistograms() {
        Set<BucketKey> allKeys = latencies.keySet();
        //System.out.println("PerfMon.draw: latencies keyset size " + allKeys.size());
        LinkedHashSet<BucketKey> sorted = new LinkedHashSet<>(allKeys.size());
        sorted.addAll(allKeys);
        for (BucketKey key : sorted) {
            int value = latencies.get(key);
            System.out.printf("  %s: %d\n", key, value);
        }
    }

    interface SerializableRunnable extends Runnable, Serializable {}

    private SerializableRunnable collect = () -> {
        //System.out.println("PerfMon: Begin collect runnable at " + System.currentTimeMillis());
        long jetCompletions = jetCompletionsThisInterval.get();
        jetCompletionsThisInterval.getAndSubtract(jetCompletions); // reset to about zero
        long imdgCompletions = imdgCompletionsThisInterval.get();
        imdgCompletionsThisInterval.getAndSubtract(imdgCompletions); // reset to about zero

        // TODO: pump to Graphite / Grafana dashboard
        if (jetCompletions > 0) {
            System.out.printf("JET  TPS %d\n", jetCompletions / (TPS_INTERVAL_MS / 1000));
            jetTotalCompletions.getAndAdd(jetCompletions);
        }
        if (imdgCompletions > 0) {
            System.out.printf("IMDG TPS %d\n", imdgCompletions / (TPS_INTERVAL_MS / 1000));
            imdgTotalCompletions.getAndAdd(imdgCompletions);
        }
        //System.out.println("PerfMon: End collect runnable at " + System.currentTimeMillis() + ", operations in flight " + operationsInProcess.size());
    };

    private SerializableRunnable output = () -> {
        drawProcessingHistograms();
    };

    static ScheduledFuture<?> collectHandle;
    static ScheduledFuture<?> outputHandle;

    public void startTimers() {
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(3);

        System.out.println("PerfMon: startTimers trying to acquire semaphores");

        try {
            collectSemaphore.acquire();
            collectHandle = executor.scheduleAtFixedRate(collect, 0, TPS_INTERVAL_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            // Do not release
        }

        try {
            outputSemaphore.acquire();
            outputHandle = executor.scheduleAtFixedRate(output, 30, HISTO_INTERVAL_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            // Do not release
        }
        System.out.println("PerfMon: Timers started");
    }

    public void stopTimers() {
        collectHandle.cancel(true);
        outputHandle.cancel(true);
    }

    private void init() {
        System.out.println("PerfMon.init()");
        HazelcastInstance hazelcast = HazelcastClient.newHazelcastClient();
        jetCompletionsThisInterval = hazelcast.getPNCounter("jetCompletionsThisInterval");
        jetTotalCompletions = hazelcast.getPNCounter("jetTotalCompletions");
        imdgCompletionsThisInterval = hazelcast.getPNCounter("imdgCompletionsThisInterval");
        imdgTotalCompletions = hazelcast.getPNCounter("imdgTotalCompletions");

        operationsInProcess = hazelcast.getMap("operationsInProgress");
        latencies = hazelcast.getMap("latencies");

        // TODO: get these from the CP subsystem
        jetTotalProcessingTime = hazelcast.getAtomicLong("jetProcessingTime");
        jetTotalEndToEndTime = hazelcast.getAtomicLong("jetEndToEndTime");
        imdgTotalProcessingTime = hazelcast.getAtomicLong("imdgProcessingTime");
        imdgTotalEndToEndTime = hazelcast.getAtomicLong("imdgEndToEndTime");

        collectExecSvc = hazelcast.getScheduledExecutorService("perfmon_collect");
        outputExecSvc  = hazelcast.getScheduledExecutorService("perfmon_output");

        CPSubsystem cp = hazelcast.getCPSubsystem();
        outputSemaphore = cp.getSemaphore("outputSemaphore");
        outputSemaphore.init(1);
        collectSemaphore = cp.getSemaphore("collectSemaphore");
        collectSemaphore.init(1);
    }


    @Override
    public void run() {
        System.out.println("PerfMonitor.run [init + startTimers]");
        init();
        startTimers();
        System.out.println("PerfMonitor.run complete");
    }
}
