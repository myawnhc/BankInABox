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

    public TransactionMapListener(HazelcastInstance instance) {
        //hazelcast = instance;
        preAuthMap = instance.getMap("preAuth");
        accountMap = instance.getMap("accountMap");
        merchantMap = instance.getMap("merchantMap");
        approved = instance.getMap("approved");
        rejectedForFraud = instance.getMap("rejectedForFraud");
        rejectedForCredit = instance.getMap("rejectedForCredit");
    }


    @Override
    public void entryAdded(EntryEvent<String, Transaction> entryEvent) {

        String transactionId = entryEvent.getKey();
        Transaction txn = entryEvent.getValue();
        Account account = accountMap.get(txn.getAccountNumber());
        Merchant merchant = merchantMap.get(txn.getMerchantId());

        FraudRulesEP fraudRulesEP = new FraudRulesEP();
        fraudRulesEP.setMerchant(merchant);

        PaymentRulesEP paymentRulesEP = new PaymentRulesEP();
        paymentRulesEP.setAccount(account);

        //System.out.println("Executing fraud rules for " + transactionId);
        Integer risk = (Integer) preAuthMap.executeOnKey(transactionId, fraudRulesEP);

        // Doesn't actually map to the high-medium-low values from the EP, but
        // assumes we'd be averaging over several rule results
        if (risk > 80) {
            preAuthMap.remove(txn);
            rejectedForFraud.put(transactionId, txn);
            return;
        }

        //System.out.println("Executing payment rules for " + transactionId);
        Boolean passed = (Boolean) preAuthMap.executeOnKey(transactionId, paymentRulesEP);
        preAuthMap.remove(txn);
        if (passed) {
            approved.put(transactionId, txn);
        } else {
            rejectedForCredit.put(transactionId, txn);
        }

    }
}