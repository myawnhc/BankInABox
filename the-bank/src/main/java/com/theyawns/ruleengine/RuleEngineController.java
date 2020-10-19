package com.theyawns.ruleengine;

import com.hazelcast.collection.IQueue;
import com.theyawns.ruleengine.rulesets.RuleSet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Rough development sketch, not yet incorporated into the app.
// Idea is to move from current hard-coded set of RuleSets (see
// PreAuthMapListener) to a dynamic configuration that can be adjusted
// while the app is running.
public class RuleEngineController<T extends HasID> {

    private Map<String, RuleSetInfo> knownRuleSets = new HashMap<>();

    // Info related to the deployment of a ruleset within the RuleEngine
    // Current thought is that this is an internal implementation detail, so we don't have a method
    // to return RuleSetInfo to external caller -- this is open to reconsideration.
    private static class RuleSetInfo<T> {
        private enum ChannelType { IQueue } // more to come
        private RuleSet ruleSet;
        private boolean enabled;
        private ChannelType channel;

        // We will expand the number of routing options as needed - it may
        // be that we support IQueue, Topic, perhaps Kafka or JMS, etc.
        // As we do so, the addRuleSet method of the outer class will be
        // overloaded to take additional communication channels
        private IQueue inputQueue;

        public RuleSetInfo (RuleSet set, IQueue channel) {
            this.ruleSet = set;
            this.inputQueue = channel;
            this.channel = ChannelType.IQueue;
        }
        // possibly version info?
        // possibly reference to a Jet job where the RuleSet is running

        // would be nice to have some metrics - pass/fail rate, for example -- not sure best place to hold that.

        public void routeItem(T item) {
            switch (channel) {
                case IQueue:
                    try {
                        inputQueue.put(item);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
            }
        }
    }

    // Expect this will get overloaded with other comm channels at some point
    public void addRuleSet(RuleSet set, IQueue queue) {
        RuleSetInfo info = new RuleSetInfo(set, queue);
        knownRuleSets.put(set.getName(), info);
    }
    public void deleteRuleSet(RuleSet set) {
        knownRuleSets.remove(set.getName());
    }
    public void updateRuleSet(RuleSet set) {
        RuleSetInfo info = knownRuleSets.get(set);
        // alternately, we could just add it
        if (info == null) throw new IllegalArgumentException(("No such ruleset"));
        info.ruleSet = set;
        // if we track some sort of version or when-deployed info, set it here ...
    }

    public List<RuleSet> getAllRuleSets() {
        List<RuleSet> allSets = new ArrayList<>(knownRuleSets.size());
        for (RuleSetInfo info : knownRuleSets.values()) {
            allSets.add(info.ruleSet);
        }
        return allSets;
    }

    public RuleSet getRuleSet(String name) {
        RuleSetInfo info = knownRuleSets.get(name);
        return info.ruleSet;
    }

    // setEnabled method not on RuleSet yet ... we can change our local status (in RuleSetInfo), but
    // nothing will actually change unless we pass through to the RuleSet itself or stop traffic from
    // reaching the RuleSet
    public void enableRuleSet(String name) {}
    public void disableRuleSet(String name) {}

    // should there be a start() method here that launches all the non-disabled
    // rulesets ?   How does that impact the ability of Launcher to monitor, or
    // should the monitoring/logging be done here instead?

    public int forwardToApplicableRuleSets(T item) {
        int counter = 0;
        for (RuleSetInfo info : knownRuleSets.values()) {
            RuleSet set = info.ruleSet;
            if (set.isApplicableTo(item)) {
                info.routeItem(item);
                counter++;
            }
        }
        return counter;
    }

}
