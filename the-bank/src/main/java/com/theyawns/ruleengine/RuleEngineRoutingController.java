package com.theyawns.ruleengine;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.map.IMap;
import com.hazelcast.replicatedmap.ReplicatedMap;
import com.theyawns.banking.Transaction;
import com.theyawns.banking.fraud.fdengine.imdgimpl.listeners.PreauthMapListener;
import com.theyawns.controller.Constants;
import com.theyawns.ruleengine.rulesets.RuleSet;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

// Send items to the applicable rulesets
public class RuleEngineRoutingController<T extends HasID> implements Runnable, Serializable, HazelcastInstanceAware
{
    private transient HazelcastInstance hazelcast;
    // NO: Use IMAP
    //private Map<String, RuleSetRoutingInfo> knownRuleSets = new HashMap<>();
    ReplicatedMap<String, RuleSetRoutingInfo> routingInfoMap;

    // Local cache of ruleset values
    boolean needsRefresh = true;
    Collection<RuleSetRoutingInfo> cache;

    // Expect this will get overloaded with other comm channels at some point
    // NOT used for initial setup (that comes from FDE), but for other
    // dynamically added items
    public void addRuleSet(RuleSet set, String queue) {
        RuleSetRoutingInfo<T> info = new RuleSetRoutingInfo(set, queue);
        //knownRuleSets.put(set.getName(), info);
        routingInfoMap.put(set.getName(), info);
        System.out.println("RERC.add (only for dynamic changes) Added RuleSet " + set.getName() + " to routing info");
    }
    public void deleteRuleSet(RuleSet set) {
        routingInfoMap.remove(set.getName());
    }
    public void updateRuleSet(RuleSet set) {
        RuleSetRoutingInfo info = routingInfoMap.get(set);
        // alternately, we could just add it
        if (info == null) throw new IllegalArgumentException(("No such ruleset"));
        routingInfoMap.put(set.getName(), info);
        // if we track some sort of version or when-deployed info, set it here ...
    }

//    public List<RuleSet> getAllRuleSets() {
//        List<RuleSet> allSets = new ArrayList<>(routingInfoMap.size());
//        for (RuleSetRoutingInfo info : routingInfoMap.values()) {
//            allSets.add(info.getRuleSet());
//        }
//        return allSets;
//    }

//    public RuleSet getRuleSet(String name) {
//        RuleSetRoutingInfo info = routingInfoMap.get(name);
//        return info.getRuleSet();
//    }

    // setEnabled method not on RuleSet yet ... we can change our local status (in RuleSetInfo), but
    // nothing will actually change unless we pass through to the RuleSet itself or stop traffic from
    // reaching the RuleSet
    public void enableRuleSet(String name) {}
    public void disableRuleSet(String name) {}

    // should there be a start() method here that launches all the non-disabled
    // rulesets ?   How does that impact the ability of Launcher to monitor, or
    // should the monitoring/logging be done here instead?

    public ItemCarrier<T> forwardToApplicableRuleSets(ItemCarrier<T> carrier) {
        List<RuleSetRoutingInfo> qualified = new ArrayList<>();
        for ( RuleSetRoutingInfo info : routingInfoMap.values()) {
            if (info.isApplicableTo(carrier.getItem())) {
                qualified.add(info);
            }
        }
        carrier.setNumberOfRuleSetsThatApply(qualified.size());
        carrier.setTimeEnqueuedForRouting();

        for ( RuleSetRoutingInfo info : qualified ) {
            info.routeItem(carrier);
        }
        return carrier;
    }

    @Override
    public void run() {
        PreauthMapListener paml = new PreauthMapListener(hazelcast, this);
        IMap<String, Transaction> preAuth = hazelcast.getMap(Constants.MAP_PREAUTH);
        preAuth.addLocalEntryListener(paml);
        //preAuth.addEntryListener(paml, true);
    }

    @Override
    public void setHazelcastInstance(HazelcastInstance hazelcastInstance) {
        System.out.println("RERC.setHazelcastInstance");
        hazelcast = hazelcastInstance;
        routingInfoMap = hazelcast.getReplicatedMap(Constants.MAP_RS_ROUTING);
    }
}
