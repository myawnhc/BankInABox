package com.theyawns.ruleengine;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IQueue;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class BaseRuleEngine<T extends HasID>  implements RuleEngine<T> {

    private HazelcastInstance hazelcast;

    protected IQueue<T> transactionQueue;  // TODO: rename as inputQueue? or streamItemQueue?

    protected Set<RuleSet> ruleSets = new HashSet<>();

    public BaseRuleEngine(HazelcastInstance instance) {
        hazelcast = instance;
        this.transactionQueue = hazelcast.getQueue("transactionQueue");
    }

    public void addRuleSet(RuleSet rules) {
        ruleSets.add(rules);
    }

    public Set<RuleSet> getRuleSets() {
        return ruleSets;
    }

//    public Set<Rule> ruleSet() {
//        return ruleSet;
//    }
//
//    //@Override
//    public void init(Set<Rule> rules) {
//        ruleSet = rules;
//    }

    // TODO: this is IMDG only, so should it move down to IMDGRuleEngine?
    //       Jet will wrap the queue as a StreamSource
    @Override
    public T getNextInput() {
        try {
            return transactionQueue.poll(10, TimeUnit.SECONDS);     // TODO: poll vs. take, and whether to use time out
        } catch (InterruptedException e) {
            e.printStackTrace();
            return null;
        }
    }

    protected HazelcastInstance getHazelcast() {
        return hazelcast;
    }


//    @Override
//    public void setInputTransactionQueue(IQueue queue) {
//        this.transactionQueue = queue;
//    }
}
