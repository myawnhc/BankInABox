package com.theyawns.perfmon;

import com.hazelcast.ringbuffer.Ringbuffer;
import com.theyawns.domain.payments.Transaction;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class PerfMonitor {

    // Expect latencies to fall in range of 1 - 100ms, so bucketing for that range
    private static int[] processingLatencies = new int[101];
    private static int[] endToEndLatencies = new int[101];
    private static int completionsThisInterval = 0;
    private static Ringbuffer<Integer> tps;

    public static void setRingBuffer(Ringbuffer<Integer> buffer) { tps = buffer; }

    public static void recordTransaction(Transaction t) {
        completionsThisInterval++;

        int pl = (int) t.processingTime.elapsed();
        if (pl < 0)
            processingLatencies[0]++;
        else if (pl > 100)
            processingLatencies[100]++;
        else
            processingLatencies[pl]++;

        int el = (int) t.processingTime.elapsed();
        if (el < 0)
            endToEndLatencies[0]++;
        else if (el > 100)
            endToEndLatencies[100]++;
        else
            endToEndLatencies[el]++;
    }

    public static void drawProcessingHistogram() {
        for (int i=0; i<101; i++) {
            if (processingLatencies[i] == 0) continue;
            System.out.printf("%d ms  %d\n ", i, processingLatencies[i]);
        }
    }

    private static final int TPS_INTERVAL_MS = 5000;
    private static final int HISTO_INTERVAL_MS = 30000;

    private static Runnable collect = () -> {
            int copy = completionsThisInterval;
            completionsThisInterval = 0;
            tps.add(copy);
            // TODO: pump to Graphite / Grafana dashboard
            System.out.println("TPS this interval: " + copy);
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
