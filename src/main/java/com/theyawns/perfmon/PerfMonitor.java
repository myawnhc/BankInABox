package com.theyawns.perfmon;

import com.hazelcast.ringbuffer.Ringbuffer;
import com.theyawns.domain.payments.Transaction;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class PerfMonitor {

    // Expect latencies to fall in range of 1 - 100ms, so bucketing for that range
    private static int[] JetProcessingLatencies = new int[101];
    private static int[] IMDGProcessingLatencies = new int[101];

    private static int[] JetEndToEndLatencies = new int[101];
    private static int[] IMDGendToEndLatencies = new int[101];

    private static int jetCompletionsThisInterval = 0;
    private static int jetTotalCompletions = 0;
    private static int IMDGCompletionsThisInterval = 0;
    private static int IMDGTotalCompletions = 0;

    private static Ringbuffer<Integer> jetTPS;
    private static Ringbuffer<Integer> imdgTPS;

    public static void setRingBuffers(Ringbuffer<Integer> jet, Ringbuffer<Integer> imdg) {
        jetTPS = jet;
        imdgTPS = imdg;
    }

    public static void recordTransaction(String key, Transaction t) {

        if (key.equals("Jet") )
            jetCompletionsThisInterval++;
        else
            IMDGCompletionsThisInterval++;

        int pl = (int) t.processingTime.elapsed();
        if (key.equals("IMDG")) {
            if (pl < 0)
                IMDGProcessingLatencies[0]++;
            else if (pl > 100)
                IMDGProcessingLatencies[100]++;
            else
                IMDGProcessingLatencies[pl]++;

            int el = (int) t.endToEndTime.elapsed();
            if (el < 0)
                IMDGendToEndLatencies[0]++;
            else if (el > 100)
                IMDGendToEndLatencies[100]++;
            else
                IMDGendToEndLatencies[el]++;
        } else {
            if (pl < 0)
                JetProcessingLatencies[0]++;
            else if (pl > 100)
                JetProcessingLatencies[100]++;
            else
                JetProcessingLatencies[pl]++;

            int el = (int) t.endToEndTime.elapsed();
            if (el < 0)
                JetEndToEndLatencies[0]++;
            else if (el > 100)
                JetEndToEndLatencies[100]++;
            else
                JetEndToEndLatencies[el]++;
        }
    }

    public static void drawProcessingHistogram() {
        for (int i=0; i<101; i++) {
            // Graph exhibits a long tail, for purposes on dumping to console drop if < 1% of total
            if (JetProcessingLatencies[i] <= jetTotalCompletions* .01)
                continue;
            System.out.printf("  Jet Processing:  %d ms  %d\n", i, JetProcessingLatencies[i]);
        }

        for (int i=0; i<101; i++) {
            // Graph exhibits a long tail, for purposes on dumping to console drop if < 1% of total
            if (JetEndToEndLatencies[i] <= jetTotalCompletions* .01)
                continue;
            System.out.printf("  Jet End to End:  %d ms  %d\n", i, JetEndToEndLatencies[i]);
        }

        for (int i=0; i<101; i++) {
            if (IMDGProcessingLatencies[i] <= IMDGTotalCompletions * .01)
                continue;
            System.out.printf("  IMDG Processing: %d ms  %d\n", i, IMDGProcessingLatencies[i]);
        }

        for (int i=0; i<101; i++) {
            if (IMDGendToEndLatencies[i] <= IMDGTotalCompletions * .01)
                continue;
            System.out.printf("  IMDG End to End: %d ms  %d\n", i, IMDGendToEndLatencies[i]);
        }
    }

    private static final int TPS_INTERVAL_MS = 5000;
    private static final int HISTO_INTERVAL_MS = 30000;

    private static Runnable collect = () -> {
        jetTPS.add(jetCompletionsThisInterval);
        imdgTPS.add(IMDGCompletionsThisInterval);
        // TODO: pump to Graphite / Grafana dashboard
        if (jetCompletionsThisInterval > 0) {
            System.out.printf("JET  TPS %d\n", jetCompletionsThisInterval / (TPS_INTERVAL_MS / 1000));
            jetTotalCompletions += jetCompletionsThisInterval;
            jetCompletionsThisInterval = 0;
        }
        if (IMDGCompletionsThisInterval > 0) {
            System.out.printf("IMDG TPS %d\n", IMDGCompletionsThisInterval / (TPS_INTERVAL_MS / 1000));
            IMDGTotalCompletions += IMDGCompletionsThisInterval;
            IMDGCompletionsThisInterval = 0;
        }
    };

    private static Runnable output = () -> {
        drawProcessingHistogram();
    };

    static ScheduledFuture<?> collectHandle;
    static ScheduledFuture<?> outputHandle;

    public static void startTimers() {
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
        collectHandle = executor.scheduleAtFixedRate(collect, 0, TPS_INTERVAL_MS, TimeUnit.MILLISECONDS);
        outputHandle = executor.scheduleAtFixedRate(output, 30, HISTO_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    public static void stopTimers() {
        collectHandle.cancel(true);
        outputHandle.cancel(true);
    }
}
