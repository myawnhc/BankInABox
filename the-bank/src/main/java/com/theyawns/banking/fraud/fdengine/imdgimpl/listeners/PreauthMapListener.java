package com.theyawns.banking.fraud.fdengine.imdgimpl.listeners;

import com.hazelcast.collection.IQueue;
import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.crdt.pncounter.PNCounter;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.hazelcast.map.IMap;
import com.hazelcast.map.listener.EntryAddedListener;
import com.theyawns.controller.Constants;
import com.theyawns.banking.Transaction;
import com.theyawns.controller.Constants;
import com.theyawns.ruleengine.ItemCarrier;
import com.theyawns.ruleengine.RuleEngineRoutingController;

// TODO: This can probably be abstracted to not be domain-specific --
//     wrap an item, route it, mark Q'd time are all generic
// Reference to specific RuleSets and Queues is domain-specific but this
//   will be extracting into a RuleEngineRoutingInfo IMap that the
//   routing controller will use to route incoming items to appropriate
//   rulesets.

public class PreauthMapListener implements
        EntryAddedListener<String, Transaction> {

    private final static ILogger log = Logger.getLogger(PreauthMapListener.class);

    //private HazelcastInstance hazelcast;
    //private IMap<String, Transaction> preAuthMap;


    // Counters for Grafana dashboard
    private PNCounter merchant_txn_count_walmart;
    private PNCounter merchant_txn_count_amazon;

    private RuleEngineRoutingController<Transaction> routingController;


    public PreauthMapListener(HazelcastInstance instance, RuleEngineRoutingController rec) {
        //preAuthMap = instance.getMap(Constants.MAP_PREAUTH);
        merchant_txn_count_amazon = instance.getPNCounter(Constants.PN_COUNT_AMAZON);
        merchant_txn_count_walmart = instance.getPNCounter(Constants.PN_COUNT_WALMART);
        this.routingController = rec;
    }

    @Override
    public void entryAdded(EntryEvent<String, Transaction> entryEvent) {

        // In the future, we might split queues to better distribute the workload; so
        // we call a function to return the appropriate queue.  In this version of the
        // demo we'll always get the same queue for each set of rules, but we could
        // easily scale this up by using round-robin, modulo the transaction id, or
        // some other scheme
        if (! entryEvent.getMember().localMember())  {
            //System.out.println("remote event ignored");
            return;
        }

        Transaction txn = entryEvent.getValue();
        accumulateMerchantStats(txn);

        ItemCarrier<Transaction> carrier = new ItemCarrier<>(txn);
        carrier.setNumberOfRuleSetsThatApply(2);
        carrier.setTimeEnqueuedForRouting(); // sets to now
        routingController.forwardToApplicableRuleSets(carrier);
    }

    // This could move to a domain-specific subclass, and the get-and-route
    // code could be generic.
    private void accumulateMerchantStats(Transaction txn) {
        int merchantNum = 1;
        try {
            merchantNum = Integer.parseInt(txn.getMerchantId());  // TODO: see a rare number format exception here - null merchant id
        } catch (NumberFormatException nfe) {
            log.warning(("Number format exception parsing merchant: " + txn + ", transaction will be skipped"));
            return; // Do not process the item
        }
        if (merchantNum >= 1 && merchantNum <= 9)
            merchant_txn_count_amazon.getAndIncrement();
        else if (merchantNum >= 10 && merchantNum <= 20)
            merchant_txn_count_walmart.getAndIncrement();
    }
}
