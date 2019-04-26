package com.theyawns.perfmon;

import java.io.Serializable;

public class LatencyMetric implements Serializable {

    private long start;
    private long stop;

    public void start() { start = System.currentTimeMillis(); }
    public void stop() {
        stop = System.currentTimeMillis();
        // TODO: increment TPS
        // TODO: post elapsed
    }
    public long elapsed() { return stop - start; }
}
