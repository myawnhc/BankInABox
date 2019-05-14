package com.theyawns.listeners;

import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.map.listener.EntryAddedListener;
import com.theyawns.domain.payments.Account;
import com.theyawns.domain.payments.Merchant;
import com.theyawns.domain.payments.Transaction;
import com.theyawns.entryprocessors.FraudRulesEP;
import com.theyawns.entryprocessors.PaymentRulesEP;
import com.theyawns.launcher.BankInABoxProperties;
import com.theyawns.perfmon.PerfMonitor;
import com.theyawns.sink.Graphite;

import java.io.IOException;


// Listener is armed by Launcher, instance should be the non-Jet IMDG cluster

/** Listener to the preAuth map to run EntryProcessor for fraud detection */
public class TransactionMapListener implements
        EntryAddedListener<String, Transaction> {

    //private HazelcastInstance hazelcast;
    private IMap<String, Transaction> preAuthMap;
    private IMap<String, Account> accountMap;
    private IMap<String, Merchant> merchantMap;
    private IMap<String, Transaction> approved;
    private IMap<String, Transaction> rejectedForFraud;
    private IMap<String, Transaction> rejectedForCredit;


    Graphite graphite;

    public TransactionMapListener(HazelcastInstance instance) {
        //hazelcast = instance;
        preAuthMap = instance.getMap("preAuth");
        accountMap = instance.getMap("accountMap");
        merchantMap = instance.getMap("merchantMap");
        approved = instance.getMap("approved");
        rejectedForFraud = instance.getMap("rejectedForFraud");
        rejectedForCredit = instance.getMap("rejectedForCredit");

        graphite = new Graphite();
    }


    long counter=0;

    @Override
    public void entryAdded(EntryEvent<String, Transaction> entryEvent) {
        //System.out.println("TransactionMapListener.entryAdded");
        // write out every so often
        if( (++counter % 10)==0 ) {
            try {
                graphite.writeStats("bib.payments.amazon",preAuthMap.size());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        String transactionId = entryEvent.getKey();
        Transaction txn = entryEvent.getValue();

        //txn.processingTime.start(); // Start clock for processing time latency metric
        Account account = accountMap.get(txn.getAccountNumber());
        Merchant merchant = merchantMap.get(txn.getMerchantId());

        //
        // FRAUD
        //

        FraudRulesEP fraudRulesEP = new FraudRulesEP();
        fraudRulesEP.setMerchant(merchant);

        //System.out.println("Executing fraud rules for " + transactionId);
        Integer risk = (Integer) preAuthMap.executeOnKey(transactionId, fraudRulesEP);

        // EP will update the transaction, but we are continuing to use the local version!
        // Don't want to keep putting new transactions in the map just to update the times,
        // need to re-think this aspect of the design.  For now, update our local copy.
        //txn.processingTime.stop();
        if (false /*BankInABoxProperties.COLLECT_PERFORMANCE_STATS*/) {
            // TODO: hard-coded name here is wrong .. .this is actually fraud rules, not credit
            PerfMonitor.getInstance().endLatencyMeasurement(PerfMonitor.Platform.IMDG, PerfMonitor.Scope.Processing,
                    "CreditLimitRule", txn.getID());
            //PerfMonitor.getInstance().recordTransaction("IMDG", txn); // may move this to a map listener on rejected so can capture end-to-end time

        }

        // Doesn't actually map to the high-medium-low values from the EP, but
        // assumes we'd be averaging over several rule results
        if (risk > 80) {
            preAuthMap.remove(txn.getID());
            rejectedForFraud.put(transactionId, txn);
            //txn.endToEndTime.stop();
            if (false /*BankInABoxProperties.COLLECT_PERFORMANCE_STATS*/) {
                // TODO: hard-coded name here is wrong .. .this is actually fraud rules, not credit
                PerfMonitor.getInstance().endLatencyMeasurement(PerfMonitor.Platform.IMDG, PerfMonitor.Scope.EndToEnd,
                        "CreditLimitRule", txn.getID());
                PerfMonitor.getInstance().recordTransaction("IMDG", txn); // may move this to a map listener on rejected so can capture end-to-end time

            }
            return;
        }

        //
        // PAYMENT
        //

        if (BankInABoxProperties.COLLECT_LATENCY_STATS) {
            PerfMonitor.getInstance().beginLatencyMeasurement(PerfMonitor.Platform.IMDG, PerfMonitor.Scope.Processing,
                    "CreditLimitRule", txn.getID());
        }

        PaymentRulesEP paymentRulesEP = new PaymentRulesEP();
        paymentRulesEP.setAccount(account);

        //System.out.println("Executing payment rules for " + transactionId);
        Boolean passed = (Boolean) preAuthMap.executeOnKey(transactionId, paymentRulesEP);
        preAuthMap.remove(txn.getID());
        if (passed) {
            approved.put(transactionId, txn);
        } else {
            rejectedForCredit.put(transactionId, txn);
        }
        if (BankInABoxProperties.COLLECT_LATENCY_STATS) {
            PerfMonitor.getInstance().endLatencyMeasurement(PerfMonitor.Platform.IMDG, PerfMonitor.Scope.Processing,
                    "CreditLimitRule", txn.getID());
            PerfMonitor.getInstance().endLatencyMeasurement(PerfMonitor.Platform.IMDG, PerfMonitor.Scope.EndToEnd,
                    "CreditLimitRule", txn.getID());
        } if (BankInABoxProperties.COLLECT_TPS_STATS) {
            PerfMonitor.getInstance().recordTransaction("IMDG", txn); // may move this to a map listener on rejected so can capture end-to-end time
        }
    }

}