package com.theyawns.banking.fraud.fdengine.imdgimpl.listeners;

import com.hazelcast.collection.IQueue;
import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.crdt.pncounter.PNCounter;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.hazelcast.map.IMap;
import com.hazelcast.map.listener.EntryAddedListener;
import com.hazelcast.map.listener.EntryLoadedListener;
import com.theyawns.controller.Constants;
import com.theyawns.banking.Transaction;
import com.theyawns.controller.Constants;
import com.theyawns.ruleengine.ItemCarrier;
import com.theyawns.ruleengine.RuleEngineController;

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
    private IMap<String, Transaction> preAuthMap;

    // Queues to pass to RuleExecutors -- TODO: replace these with a single ReliableTopic
    //private ITopic<Transaction> preAuthTopic;
    private IQueue<ItemCarrier<Transaction>> locationRulesQueue;
    private IQueue<ItemCarrier<Transaction>> merchantRulesQueue;
//    private IQueue<ItemCarrier<Transaction>> paymentRulesQueue;

    // Counters for Grafana dashboard
    private PNCounter merchant_txn_count_walmart;
    private PNCounter merchant_txn_count_amazon;

    private RuleEngineController ruleEngineController;


    public PreauthMapListener(HazelcastInstance instance, RuleEngineController rec) {
        preAuthMap = instance.getMap(Constants.MAP_PREAUTH);
        //preAuthTopic = instance.getReliableTopic(Constants.TOPIC_PREAUTH);
        locationRulesQueue = instance.getQueue(Constants.QUEUE_LOCATION);
        merchantRulesQueue = instance.getQueue(Constants.QUEUE_MERCHANT);
        //paymentRulesQueue = instance.getQueue(Constants.QUEUE_CREDITRULES);
//        locationRulesQueue = instance.getQueue(Constants.QUEUE_LOCATION);
//        merchantRulesQueue = instance.getQueue(Constants.QUEUE_MERCHANT);
//        paymentRulesQueue = instance.getQueue(Constants.QUEUE_CREDITRULES);
        merchant_txn_count_amazon = instance.getPNCounter(Constants.PN_COUNT_AMAZON);
        merchant_txn_count_walmart = instance.getPNCounter(Constants.PN_COUNT_WALMART);
        this.ruleEngineController = rec;
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

        //log.finest("entryAdded key " + entryEvent.getKey() + " value " + entryEvent.getValue());
        //ItemCarrier<Transaction> carrier = entryEvent.getValue();
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

//        int routedToCounter = ruleEngineController.forwardToApplicableRuleSets(txn);
//        txn.setNumberOfRuleSetsThatApply(routedToCounter);
//        txn.setTimeEnqueuedForRuleEngine(); // sets to now
//        preAuthMap.set(txn.getItemID(), txn); // rewrite the transaction so that the ruleset field is set in the map
//        String key = txn.getItemID();
//        if (key == null) return;


        /* Ideally, the RuleEngineController could forward the transaction to
         * appliable rulesets and return to us the value to update in the
         * transaction.  This proved to add about 100ms of latency per transaction
         * so has been backed out and we'll stick with the hard-coded routing
         * until a more efficient routing can be designed.
         *
         * int routedToCounter = ruleEngineController.forwardToApplicableRuleSets(txn);
         */
        ItemCarrier<Transaction> carrier = new ItemCarrier<>(txn);

        carrier.setNumberOfRuleSetsThatApply(2);

        // This is a better representation of 'time queued' than having preAuthLoader set the
        // time in a batch of 10K items all pushed at once using putMany!
        carrier.setTimeEnqueuedForRouting(); // sets to now

        // Now that carrier has the latency and writing info, no need to rewrite the input txn
        //preAuthMap.set(txn.getItemID(), txn); // rewrite the transaction so that the ruleset field is set in the map

        // see comment block above
//        getLocationRulesQueue(txn).add(carrier);
//        getMerchantRulesQueue(txn).add(carrier);
        //System.out.println("PreauthMapListener distributed transaction to queues");

    }
}
