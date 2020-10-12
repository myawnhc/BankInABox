package com.theyawns.perfmon;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;
import com.theyawns.Constants;

import java.io.IOException;
import java.io.Serializable;

@Deprecated
public class LatencyMetric implements IdentifiedDataSerializable, Serializable {

    private long start = -1;
    private long stop  = -1;

    public void start() { start = System.currentTimeMillis(); }
    public void stop() {
        stop = System.currentTimeMillis();
    }
    public long elapsed() { return stop - start; }
    public boolean isValid() { return start >= 0 && stop >= 0; }

    public String toString() {
        return "Latency: start " + start + " stop " + stop + " elapsed " + elapsed();
    }

    @Override
    public int getFactoryId() {
        return Constants.IDS_FACTORY_ID;
    }

    @Override
    public int getClassId() {
        return Constants.IDS_LATENCY_METRIC;
    }

    @Override
    public void writeData(ObjectDataOutput objectDataOutput) throws IOException {
        objectDataOutput.writeLong(start);
        objectDataOutput.writeLong(stop);
    }

    @Override
    public void readData(ObjectDataInput objectDataInput) throws IOException {
        start = objectDataInput.readLong();
        stop = objectDataInput.readLong();
    }
}
