package com.theyawns.listeners;

import com.hazelcast.core.*;
import com.hazelcast.crdt.pncounter.PNCounter;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.hazelcast.map.listener.EntryAddedListener;

import com.hazelcast.map.listener.EntryLoadedListener;
import com.theyawns.Constants;
import com.theyawns.domain.payments.Account;
import com.theyawns.domain.payments.Merchant;
import com.theyawns.domain.payments.Transaction;


public class PreauthMapListener implements
        EntryAddedListener<String, Transaction>,
        EntryLoadedListener<String, Transaction> {

    private final static ILogger log = Logger.getLogger(PreauthMapListener.class);

    //private HazelcastInstance hazelcast;
    private IMap<String, Transaction> preAuthMap;
    private IMap<String, Merchant> merchantMap;
    private IMap<String, Account> accountMap;  // not used here but getting ref triggers eager load

    // Queues to pass to RuleExecutors -- TODO: replace these with a single ReliableTopic
    //private ITopic<Transaction> preAuthTopic;
    private IQueue<Transaction> locationRulesQueue;
    private IQueue<Transaction> merchantRulesQueue;
    private IQueue<Transaction> paymentRulesQueue;

    // Counters for Grafana dashboard
    private PNCounter merchant_txn_count_walmart;
    private PNCounter merchant_txn_count_amazon;


    public PreauthMapListener(HazelcastInstance instance) {
        //hazelcast = instance;
        preAuthMap = instance.getMap(Constants.MAP_PREAUTH);
        merchantMap = instance.getMap(Constants.MAP_MERCHANT);
        accountMap = instance.getMap(Constants.MAP_ACCOUNT);
        //preAuthTopic = instance.getReliableTopic(Constants.TOPIC_PREAUTH);
        locationRulesQueue = instance.getQueue(Constants.QUEUE_LOCATION);
        merchantRulesQueue = instance.getQueue(Constants.QUEUE_MERCHANT);
        paymentRulesQueue = instance.getQueue(Constants.QUEUE_CREDITRULES);
        merchant_txn_count_amazon = instance.getPNCounter(Constants.PN_COUNT_AMAZON);
        merchant_txn_count_walmart = instance.getPNCounter(Constants.PN_COUNT_WALMART);
    }

    public IQueue<Transaction> getLocationRulesQueue(Transaction t) {
        return locationRulesQueue;
    }

    public IQueue<Transaction> getMerchantRulesQueue(Transaction t) {
        return merchantRulesQueue;
    }

    public IQueue<Transaction> getPaymentRulesQueue(Transaction t) {
        return paymentRulesQueue;
    }

    @Override
    public void entryLoaded(EntryEvent<String, Transaction> entryEvent) {
        entryAdded(entryEvent);
    }

    @Override
    public void entryAdded(EntryEvent<String, Transaction> entryEvent) {

        // In the future, we might split queues to better distribute the workload; so
        // we call a function to return the appropriate queue.  In this version of the
        // demo we'll always get the same queue for each set of rules, but we could
        // easily scale this up by using round-robin, modulo the transaction id, or
        // some other scheme

        Transaction txn = entryEvent.getValue();
        // TODO: add averarge transaction volume to merchants, use to scale
        //       transactions appropriately.   Until that is in place, we fudge the
        //       numbers by using multiple merchants to represent the big two
        int merchantNum = Integer.parseInt(txn.getMerchantId());  // TODO: see a rare number format exception here - null merchant id
        if (merchantNum >= 1 && merchantNum <= 9)
            merchant_txn_count_amazon.getAndIncrement();
        else if (merchantNum >= 10 && merchantNum <= 20)
            merchant_txn_count_walmart.getAndIncrement();

        // Keep this in sync with the number of rulesets that are active -
        txn.setRuleSetsToApply(2);
        preAuthMap.put(txn.getItemID(), txn);

        // TODO: Replace Queues with a ReliableTopic
        //getPaymentRulesQueue(txn).add(txn);
        getLocationRulesQueue(txn).add(txn);
        getMerchantRulesQueue(txn).add(txn);
        //preAuthTopic.publish(txn);
        //System.out.println("PreauthMapListener distributed transaction to queues");

    }
}
