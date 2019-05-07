package com.theyawns.perfmon;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IAtomicLong;
import com.hazelcast.core.IMap;
import com.hazelcast.crdt.pncounter.PNCounter;
import com.hazelcast.ringbuffer.Ringbuffer;
import com.theyawns.domain.payments.Transaction;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class PerfMonitor implements Runnable {

    // Allow calls when not enabled, just make them nops
    private static boolean running = false;

    private PNCounter jetCompletionsThisInterval;
    private PNCounter imdgCompletionsThisInterval;
    private PNCounter jetTotalCompletions;
    private PNCounter imdgTotalCompletions;

    private IAtomicLong jetTotalProcessingTime;
    private IAtomicLong jetTotalEndToEndTime;
    private IAtomicLong imdgTotalProcessingTime;
    private IAtomicLong imdgTotalEndToEndTime;

    // IMDG processing time appears accurate; Jet processing and both end-to-end figures are not.
    // Instead of histogram approach, replacing with average (see new Longs introduced above)
    private IMap<String, Integer[]> processingLatencies;
    private IMap<String, Integer[]> endToEndLatencies;

    private static Ringbuffer<Long> jetTPS;
    private static Ringbuffer<Long> imdgTPS;

    private static PerfMonitor instance;

    private PerfMonitor() {}

    public static PerfMonitor getInstance() {
        if (instance == null) {
            instance = new PerfMonitor();
        }
        return instance;
    }

    /* Right now this is just measuring a single transaction, and comparing the Jet and
     * IMDG implementations.  A more robust version could take transaction name as part of the
     * key and accumulate TPS / latency info for many different transactions; that would require
     * more buckets for the results and reworking the logic but I think that's the direction this
     * will eventually take.
     */
    public void recordTransaction(String key, Transaction t) {
        if (!running)
            return;

        if (key.equals("Jet") )
            jetCompletionsThisInterval.getAndIncrement();
        else
            imdgCompletionsThisInterval.getAndIncrement();

        Integer[] proc = processingLatencies.get(key);
        if (t.processingTime.isValid()) {
            int pl = (int) t.processingTime.elapsed();
            if (key.equals("Jet"))
                jetTotalProcessingTime.getAndAdd(pl);
            else
                imdgTotalProcessingTime.getAndAdd(pl);
            if (pl < 0)
                proc[0]++;
            else if (pl > 100)
                proc[100]++;
            else
                proc[pl]++; //System.out.printf("%s pl[%d]=%d\n", key, pl, proc[pl]);
            processingLatencies.put(key, proc);
        }

        Integer[] e2e = endToEndLatencies.get(key);
        if (t.endToEndTime.isValid()) {
            int el = (int) t.endToEndTime.elapsed();
            if (key.equals("Jet"))
                jetTotalEndToEndTime.getAndAdd(el);
            else
                imdgTotalEndToEndTime.getAndAdd(el);
            if (el < 0)
                e2e[0]++;
            else if (el > 100)
                e2e[100]++;
            else
                e2e[100]++;
            endToEndLatencies.put(key, e2e);
        }

    }

    public void drawProcessingHistogram() {
        try {
            Integer[] jetProc = processingLatencies.get("Jet");
            if (jetProc != null) {
                for (int i = 0; i < 100; i++) {
                    // Graph exhibits a long tail, for purposes on dumping to console drop if < 1% of total
                    if (jetProc[i] <= jetTotalCompletions.get() * .01)
                        continue;
                    System.out.printf("  Jet Processing:  %d ms  %d\n", i, jetProc[i]);
                }
            }
            if (jetTotalCompletions.get() > 0)
                System.out.printf("  Jet Average processing %d ms\n", jetTotalProcessingTime.get() / jetTotalCompletions.get());


            Integer[] jetE2E = endToEndLatencies.get("Jet");
            if (jetE2E != null) {
                for (int i = 0; i < 100; i++) {
                    // Graph exhibits a long tail, for purposes on dumping to console drop if < 1% of total
                    if (jetE2E[i] <= jetTotalCompletions.get() * .01)
                        continue;
                    System.out.printf("  Jet End to End:  %d ms  %d\n", i, jetE2E[i]);
                }
            }
            if (jetTotalCompletions.get() > 0)
                System.out.printf("  Jet Average end-to-end %d ms\n", jetTotalEndToEndTime.get() / jetTotalCompletions.get());


            Integer[] imdgProc = processingLatencies.get("IMDG");
            if (imdgProc != null) {
                for (int i = 0; i < 100; i++) {
                    if (imdgProc[i] <= imdgTotalCompletions.get() * .01)
                        continue;
                    System.out.printf("  IMDG Processing: %d ms  %d\n", i, imdgProc[i]);
                }
            }
            if (imdgTotalCompletions.get() > 0)
                System.out.printf("  IMDG Average processing %d ms\n", imdgTotalProcessingTime.get() / imdgTotalCompletions.get());


            Integer[] imdgE2E = endToEndLatencies.get("IMDG");
            if (imdgE2E != null) {
                for (int i = 0; i < 100; i++) {
                    if (imdgE2E[i] <= imdgTotalCompletions.get() * .01)
                        continue;
                    System.out.printf("  IMDG End to End: %d ms  %d\n", i, imdgE2E[i]);
                }
            }
            if (imdgTotalCompletions.get() > 0)
                System.out.printf("  IMDG Average end-to-end %d ms\n", imdgTotalEndToEndTime.get() / imdgTotalCompletions.get());
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private static final int TPS_INTERVAL_MS = 5000;
    private static final int HISTO_INTERVAL_MS = 30000;

    private  Runnable collect = () -> {
        long jetCompletions = jetCompletionsThisInterval.get();
        jetCompletionsThisInterval.getAndSubtract(jetCompletions); // reset to about zero
        long imdgCompletions = imdgCompletionsThisInterval.get();
        imdgCompletionsThisInterval.getAndSubtract(imdgCompletions); // reset to about zero

        jetTPS.add(jetCompletions);
        imdgTPS.add(imdgCompletions);

        // TODO: pump to Graphite / Grafana dashboard
        if (jetCompletions > 0) {
            System.out.printf("JET  TPS %d\n", jetCompletions / (TPS_INTERVAL_MS / 1000));
            jetTotalCompletions.getAndAdd(jetCompletions);
        }
        if (imdgCompletions > 0) {
            System.out.printf("IMDG TPS %d\n", imdgCompletions / (TPS_INTERVAL_MS / 1000));
            imdgTotalCompletions.getAndAdd(imdgCompletions);
        }
    };

    private Runnable output = () -> {
        drawProcessingHistogram();
    };

    static ScheduledFuture<?> collectHandle;
    static ScheduledFuture<?> outputHandle;

    public void startTimers() {
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(3);
        collectHandle = executor.scheduleAtFixedRate(collect, 0, TPS_INTERVAL_MS, TimeUnit.MILLISECONDS);
        outputHandle = executor.scheduleAtFixedRate(output, 30, HISTO_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    public void stopTimers() {
        collectHandle.cancel(true);
        outputHandle.cancel(true);
    }


    @Override
    public void run() {
        HazelcastInstance hazelcast = HazelcastClient.newHazelcastClient();
        jetCompletionsThisInterval = hazelcast.getPNCounter("jetCompletionsThisInterval");
        jetTotalCompletions = hazelcast.getPNCounter("jetTotalCompletions");
        imdgCompletionsThisInterval = hazelcast.getPNCounter("imdgCompletionsThisInterval");
        imdgTotalCompletions = hazelcast.getPNCounter("imdgTotalCompletions");
        jetTPS = hazelcast.getRingbuffer("jetTPS");
        imdgTPS = hazelcast.getRingbuffer("imdgTPS");

        // TODO: get this from the CP subsystem if we're sure we don't need to run on 3.11
        jetTotalProcessingTime = hazelcast.getAtomicLong("jetProcessingTime");
        jetTotalEndToEndTime = hazelcast.getAtomicLong("jetEndToEndTime");
        imdgTotalProcessingTime = hazelcast.getAtomicLong("imdgProcessingTime");
        imdgTotalEndToEndTime = hazelcast.getAtomicLong("imdgEndToEndTime");

        processingLatencies = hazelcast.getMap("processingLatencies");
        Integer[] pl = new Integer[101];
        for (int i=0; i<101; i++) { pl[i] = 0; }
        processingLatencies.put("Jet", pl);

        Integer[] ipl = new Integer[101];
        for (int i=0; i<101; i++) { ipl[i] = 0; }
        processingLatencies.put("IMDG", ipl);

        endToEndLatencies = hazelcast.getMap("endToEndLatencies");
        Integer[] jel = new Integer[101];
        for (int i=0; i<101; i++) { jel[i] = 0; }
        endToEndLatencies.put("Jet", jel);

        Integer[] iel = new Integer[101];
        for (int i=0; i<101; i++) { iel[i] = 0; }
        endToEndLatencies.put("IMDG", iel);

        startTimers();
        running = true;

    }
}
