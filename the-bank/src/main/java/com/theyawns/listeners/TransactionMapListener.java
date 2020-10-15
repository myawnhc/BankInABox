package com.theyawns.listeners;

import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.crdt.pncounter.PNCounter;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.hazelcast.map.IMap;
import com.hazelcast.map.listener.EntryAddedListener;
import com.hazelcast.replicatedmap.ReplicatedMap;
import com.theyawns.domain.payments.Account;
import com.theyawns.domain.payments.Merchant;
import com.theyawns.domain.payments.Transaction;
import com.theyawns.domain.payments.TransactionKey;
import com.theyawns.entryprocessors.FraudRulesEP;
import com.theyawns.entryprocessors.PaymentRulesEP;
import com.theyawns.sink.Graphite;

import java.util.concurrent.RejectedExecutionException;


// Listener is armed by Launcher, instance should be the non-Jet IMDG cluster

/** Listener to the preAuth map to run EntryProcessor for fraud detection */
@Deprecated   // Moving to PreauthMapListener
public class TransactionMapListener implements
        EntryAddedListener<TransactionKey, Transaction> {

    private final static ILogger log = Logger.getLogger(TransactionMapListener.class);

    // Need constants for each fraud rule for counters
    private static final int FRAUD_RULES_COUNT = 1;
    private static final int MERC_AVG_TXN_INDEX = 0;

    private static final int PAYMENT_RULES_COUNT = 1;
    private static final int CREDIT_CHECK_INDEX = 0;

    //private HazelcastInstance hazelcast;
    private IMap<TransactionKey, Transaction> preAuthMap;
    private ReplicatedMap<String, Account> accountMap;
    private ReplicatedMap<String, Merchant> merchantMap;
    private IMap<TransactionKey, Transaction> approved;
    private IMap<TransactionKey, Transaction> rejectedForFraud;
    private IMap<TransactionKey, Transaction> rejectedForCredit;

    // Counters for Grafana dashboard
    private PNCounter approvalCounter;
    private PNCounter[] rejectedForFraudCounters;
    private PNCounter[] rejectedForCreditCounters;
    private PNCounter merchant1_10;
    private PNCounter merchant11_20;

    Graphite graphite;

    public TransactionMapListener(HazelcastInstance instance) {
        //hazelcast = instance;
        preAuthMap = instance.getMap("preAuth");
        accountMap = instance.getReplicatedMap("accountMap");
        merchantMap = instance.getReplicatedMap("merchantMap");
        approved = instance.getMap("approved");
        rejectedForFraud = instance.getMap("rejectedForFraud");
        rejectedForCredit = instance.getMap("rejectedForCredit");

        // Would like this to be more configuration/table driven so that new rules can be
        // added or disabled without rebuilding the software.
        approvalCounter = instance.getPNCounter("approvalCounter");
        rejectedForFraudCounters = new PNCounter[FRAUD_RULES_COUNT];
        rejectedForFraudCounters[MERC_AVG_TXN_INDEX] = instance.getPNCounter("rejectedFraudAvgTxnAmt");
        rejectedForCreditCounters = new PNCounter[PAYMENT_RULES_COUNT];
        rejectedForCreditCounters[CREDIT_CHECK_INDEX] = instance.getPNCounter("rejectedPaymentCreditLimit");

        merchant1_10 = instance.getPNCounter("merchant1_10");
        merchant11_20 = instance.getPNCounter("merchant11_20");
        //graphite = new Graphite();
    }


    long counter=0;

    @Override
    public void entryAdded(EntryEvent<TransactionKey, Transaction> entryEvent) {
        // Just checking something for an unrelated question
        //Object source = entryEvent.getSource();
        //System.out.println("Source is " + source);

        //System.out.println("TransactionMapListener.entryAdded");
        // write out every so often
//        if( (++counter % 10)==0 ) {
//            try {
//                graphite.writeStats("bib.payments.amazon",preAuthMap.size());
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }

        TransactionKey key = entryEvent.getKey();
        Transaction txn = entryEvent.getValue();

        // Update counters for grafana's payment graph
        // Changed ranges slightly as numbers were too close
        int merchantNum = Integer.parseInt(txn.getMerchantId());
        if (merchantNum >= 1 && merchantNum <= 9)
            merchant1_10.getAndIncrement();
        else if (merchantNum >= 10 && merchantNum <= 20)
            merchant11_20.getAndIncrement();

        //txn.processingTime.start(); // Start clock for processing time latency metric
        Account account = accountMap.get(txn.getAccountNumber());
        Merchant merchant = merchantMap.get(txn.getMerchantId());

        //
        // FRAUD
        //

        FraudRulesEP fraudRulesEP = new FraudRulesEP();
        fraudRulesEP.setMerchant(merchant);

        //System.out.println("Executing fraud rules for " + transactionId);
        Integer risk = Merchant.RISK.LOW.ordinal();
        try {
            risk = (Integer) preAuthMap.executeOnKey(key, fraudRulesEP);
        } catch (RejectedExecutionException ree) {
            log.info("Rejected execution for fraud rules - have fallen behind");
        }


        // Value of 80 here resulted in about .003% fraud rate, seems unrealistically low,
        // so moving to medium risk.
        //System.out.println("FraudRisk " + risk);
        if (risk >= 60) {
            preAuthMap.remove(key);
            rejectedForFraud.set(key, txn);
            rejectedForFraudCounters[MERC_AVG_TXN_INDEX].getAndIncrement();
            return;
        }

        //
        // PAYMENT
        //

        PaymentRulesEP paymentRulesEP = new PaymentRulesEP();
        paymentRulesEP.setAccount(account);

        //System.out.println("Executing payment rules for " + transactionId);
        Boolean passed = true;
        try {
            passed = preAuthMap.executeOnKey(key, paymentRulesEP);
        } catch (RejectedExecutionException ree) {
            log.info("Rejected execution for payment rules - have fallen behind");
        }
        preAuthMap.remove(txn.getTransactionKey());
        if (passed) {
            approved.set(key, txn);
            approvalCounter.getAndIncrement();
        } else {
            rejectedForCredit.set(key, txn);
            // TODO: either move this into the EP, or have EP return which rule[s] caused
            // rejection and use here instead of hard coded value.
            rejectedForCreditCounters[CREDIT_CHECK_INDEX].getAndIncrement();
        }
    }
}