package com.theyawns.ruleengine;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;
import com.theyawns.controller.Constants;

import java.io.IOException;
import java.io.Serializable;

/** When an item comes into the RuleEngine, it is wrapped by the ItemCarrier,
 *  which allows for routing information, latency metrics, enrichment, etc.
 *  to be handled without modifying the underlying domain object
 * @param <T>
 */
public class ItemCarrier<T extends HasID> implements Serializable, IdentifiedDataSerializable {
    private T item;

    // routing
    private int numberOfRuleSetsThatApply;

    // latency
    private long timeEnqueuedForRouting;    // in preAuthMap
    private long timeEnqueuedForExecutor;   // in *RulesQueue
    private long timeEnqueuedForAggregation;    // in Completions

    // Enrichment - will handle in domain-specific subclasses when needed
    // Results - not sure if here or in subclass

    public ItemCarrier(T item) {
        this.item = item;
    }

    // For IDS Serialization
    public ItemCarrier() {}

    public T getItem() { return item; }

    public String getItemID() { return item.getItemID(); }

    public void setNumberOfRuleSetsThatApply(int count) { numberOfRuleSetsThatApply = count; }
    public int getNumberOfRuleSetsThatApply() { return numberOfRuleSetsThatApply; }

    //public void setTimeEnqueuedForRouting(long time) { timeEnqueuedForRuleEngine = time; }
    public void setTimeEnqueuedForRouting() { timeEnqueuedForRouting = System.currentTimeMillis(); }
    public long getTimeEnqueuedForRouting() { return timeEnqueuedForRouting; }

    // Return this to allow easier use in Jet pipelines
    public ItemCarrier<T> setTimeEnqueuedForExecutor() {
        timeEnqueuedForExecutor = System.currentTimeMillis();
        return this;
    }
    public long getTimeEnqueuedForExecutor() { return timeEnqueuedForExecutor; }
    public void setTimeEnqueuedForAggregator() { timeEnqueuedForAggregation = System.currentTimeMillis(); }
    public long getTimeEnqueuedForAggregator() { return timeEnqueuedForAggregation; }

    @Override
    public int getFactoryId() {
        return Constants.IDS_FACTORY_ID;
    }

    @Override
    public int getClassId() {
        return Constants.IDS_CARRIER;
    }

    @Override
    public void writeData(ObjectDataOutput objectDataOutput) throws IOException {
        objectDataOutput.writeObject(item);
        objectDataOutput.writeInt(numberOfRuleSetsThatApply);
        objectDataOutput.writeLong(timeEnqueuedForRouting);
        objectDataOutput.writeLong(timeEnqueuedForExecutor);
        objectDataOutput.writeLong(timeEnqueuedForAggregation);
    }

    @Override
    public void readData(ObjectDataInput objectDataInput) throws IOException {
        item = objectDataInput.readObject();
        numberOfRuleSetsThatApply = objectDataInput.readInt();
        timeEnqueuedForRouting = objectDataInput.readLong();
        timeEnqueuedForExecutor = objectDataInput.readLong();
        timeEnqueuedForAggregation = objectDataInput.readLong();
    }

//    public void addToQueueWaitTime(long value) {
//        timeSpentQueued += value;
//    }
//    public long getQueueWaitTime() {
//        return timeSpentQueued;
//    }
}
