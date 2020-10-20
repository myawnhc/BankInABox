package com.theyawns.banking.fraud.fdengine.imdgimpl.listeners;

import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.crdt.pncounter.PNCounter;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.hazelcast.map.IMap;
import com.hazelcast.map.listener.EntryAddedListener;
import com.theyawns.banking.Transaction;
import com.theyawns.controller.Constants;
import com.theyawns.ruleengine.RuleEngineRoutingController;


public class PreauthMapListener implements
        EntryAddedListener<String, Transaction> {

    private final static ILogger log = Logger.getLogger(PreauthMapListener.class);

    //private HazelcastInstance hazelcast;
    private IMap<String, Transaction> preAuthMap;

    // Queues to pass to RuleExecutors -- possibly replace these with a single ReliableTopic
//    //private ITopic<Transaction> preAuthTopic;
//    private IQueue<Transaction> locationRulesQueue;
//    private IQueue<Transaction> merchantRulesQueue;
//    private IQueue<Transaction> paymentRulesQueue;

    // Counters for Grafana dashboard
    private PNCounter merchant_txn_count_walmart;
    private PNCounter merchant_txn_count_amazon;

    private RuleEngineRoutingController routingController;


    public PreauthMapListener(HazelcastInstance instance, RuleEngineRoutingController rec) {
        preAuthMap = instance.getMap(Constants.MAP_PREAUTH);
        //preAuthTopic = instance.getReliableTopic(Constants.TOPIC_PREAUTH);
//        locationRulesQueue = instance.getQueue(Constants.QUEUE_LOCATION);
//        merchantRulesQueue = instance.getQueue(Constants.QUEUE_MERCHANT);
//        paymentRulesQueue = instance.getQueue(Constants.QUEUE_CREDITRULES);
        merchant_txn_count_amazon = instance.getPNCounter(Constants.PN_COUNT_AMAZON);
        merchant_txn_count_walmart = instance.getPNCounter(Constants.PN_COUNT_WALMART);
        routingController = rec;
    }

//    public IQueue<Transaction> getLocationRulesQueue(Transaction t) {
//        return locationRulesQueue;
//    }
//
//    public IQueue<Transaction> getMerchantRulesQueue(Transaction t) {
//        return merchantRulesQueue;
//    }

    //public IQueue<Transaction> getPaymentRulesQueue(Transaction t) {
    //    return paymentRulesQueue;
    //}

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

        //log.finest("entryAdded key " + entryEvent.getKey() + " value " + entryEvent.getValue());
        Transaction txn = entryEvent.getValue();
        // TODO: add average transaction volume to merchants, use to scale
        //       transactions appropriately.   Until that is in place, we fudge the
        //       numbers by using multiple merchants to represent the big two
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

        int routedToCounter = routingController.forwardToApplicableRuleSets(txn);
        txn.setNumberOfRuleSetsThatApply(routedToCounter);
        txn.setTimeEnqueuedForRuleEngine(); // sets to now
        preAuthMap.set(txn.getItemID(), txn); // rewrite the transaction so that the ruleset field is set in the map
    }
}
