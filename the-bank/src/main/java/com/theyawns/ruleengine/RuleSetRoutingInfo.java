package com.theyawns.ruleengine;

import com.hazelcast.collection.IQueue;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.theyawns.ruleengine.rulesets.RuleSet;
import com.theyawns.ruleengine.rulesets.RuleSetSelectionFilter;

import java.io.Serializable;

// Info related to the deployment of a ruleset within the RuleEngine
// Will be stored in IMap RuleSetRoutingMap
public class RuleSetRoutingInfo<T> implements Serializable, HazelcastInstanceAware  {

    private enum ChannelType {IQueue} // more to come

    private transient HazelcastInstance hazelcast;

    //private RuleSet ruleSet;
    private RuleSetSelectionFilter<T> filter;
    private boolean enabled;
    private ChannelType channel;
    // possibly version info?
    // possibly reference to a Jet job where the RuleSet is running

    // We will expand the number of routing options as needed - it may
    // be that we support IQueue, Topic, perhaps Kafka or JMS, etc.
    // As we do so, the addRuleSet method of the outer class will be
    // overloaded to take additional communication channels
    private String queueName;
    private transient IQueue inputQueue;

    public RuleSetRoutingInfo(RuleSet set, String queueName) {
        this.filter = set.getSelectionFilter();
        this.queueName = queueName;
        this.channel = ChannelType.IQueue;
    }

    public boolean isApplicableTo(T item) {
        return filter.apply(item);
    }

    // channel & queue are internal for IMDG, but name exposed for Jet
    public String getQueueName() { return queueName; }

//    public RuleSet getRuleSet() { return ruleSet; }
//    public void setRuleSet(RuleSet set) { ruleSet = set; }

    public void routeItem(T item) {
        switch (channel) {
            case IQueue:
                try {
                    inputQueue.put(item);
                    //System.out.println("Put " + item + " on " + inputQueue.getName());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
        }
    }

    @Override
    public void setHazelcastInstance(HazelcastInstance hazelcastInstance) {
        this.hazelcast = hazelcastInstance;
        this.inputQueue = hazelcast.getQueue(queueName);
    }
}
