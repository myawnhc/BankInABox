/*
 *  Copyright 2018-2021 Hazelcast, Inc
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.package com.theyawns.controller.launcher;
 */

package com.theyawns.banking.fraud.fdengine.imdgimpl.holding;

import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.crdt.pncounter.PNCounter;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.hazelcast.map.IMap;
import com.hazelcast.map.listener.EntryAddedListener;
import com.hazelcast.replicatedmap.ReplicatedMap;
import com.theyawns.banking.Account;
import com.theyawns.banking.Merchant;
import com.theyawns.banking.Transaction;
import com.theyawns.banking.fraud.fdengine.imdgimpl.holding.entryprocessors.FraudRulesEP;
import com.theyawns.banking.fraud.fdengine.imdgimpl.holding.entryprocessors.PaymentRulesEP;
import com.theyawns.banking.fraud.fdengine.jetimpl.sink.Graphite;

import java.util.concurrent.RejectedExecutionException;


// Listener is armed by Launcher, instance should be the non-Jet IMDG cluster

/** Listener to the preAuth map to run EntryProcessor for fraud detection */
@Deprecated   // Moving to PreauthMapListener
public class TransactionMapListener implements
        EntryAddedListener<String, Transaction> {

    private final static ILogger log = Logger.getLogger(TransactionMapListener.class);

    // Need constants for each fraud rule for counters
    private static final int FRAUD_RULES_COUNT = 1;
    private static final int MERC_AVG_TXN_INDEX = 0;

    private static final int PAYMENT_RULES_COUNT = 1;
    private static final int CREDIT_CHECK_INDEX = 0;

    //private HazelcastInstance hazelcast;
    private IMap<String, Transaction> preAuthMap;
    private ReplicatedMap<String, Account> accountMap;
    private ReplicatedMap<String, Merchant> merchantMap;
    private IMap<String, Transaction> approved;
    private IMap<String, Transaction> rejectedForFraud;
    private IMap<String, Transaction> rejectedForCredit;

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
    public void entryAdded(EntryEvent<String, Transaction> entryEvent) {
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

        String transactionId = entryEvent.getKey();
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
            risk = (Integer) preAuthMap.executeOnKey(transactionId, fraudRulesEP);
        } catch (RejectedExecutionException ree) {
            log.info("Rejected execution for fraud rules - have fallen behind");
        }

        // Value of 80 here resulted in about .003% fraud rate, seems unrealistically low,
        // so moving to medium risk.
        //System.out.println("FraudRisk " + risk);
        if (risk >= 60) {
            preAuthMap.remove(txn.getItemID());
            rejectedForFraud.set(transactionId, txn);
            rejectedForFraudCounters[MERC_AVG_TXN_INDEX].getAndIncrement();
            //txn.endToEndTime.stop();
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
            passed = preAuthMap.executeOnKey(transactionId, paymentRulesEP);
        } catch (RejectedExecutionException ree) {
            log.info("Rejected execution for payment rules - have fallen behind");
        }
        preAuthMap.remove(txn.getItemID());
        if (passed) {
            approved.set(transactionId, txn);
            approvalCounter.getAndIncrement();
        } else {
            rejectedForCredit.set(transactionId, txn);
            // TODO: either move this into the EP, or have EP return which rule[s] caused
            // rejection and use here instead of hard coded value.
            rejectedForCreditCounters[CREDIT_CHECK_INDEX].getAndIncrement();
        }
    }
}