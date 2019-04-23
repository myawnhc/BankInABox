package com.theyawns.entryprocessors;

import com.hazelcast.map.EntryBackupProcessor;
import com.hazelcast.map.EntryProcessor;
import com.theyawns.domain.payments.Merchant;
import com.theyawns.domain.payments.Transaction;

import java.io.Serializable;
import java.util.Map;

public class FraudRulesEP implements EntryProcessor<String, Transaction>, Serializable {

    private Merchant merchant;

    public void setMerchant(Merchant m) { this.merchant = m; }

    @Override
    public Object process(Map.Entry<String, Transaction> entry) {
        System.out.println("Processing FraudRulesEP");
        if (merchant == null) {
            throw new IllegalStateException("Merchant must be set prior to invoking EntryProcessor");
        }
        Transaction txn = entry.getValue();

        MerchantTxnAvgCheck mtac = new MerchantTxnAvgCheck();
        mtac.setMerchant(merchant);

        Merchant.RISK txnAmountRisk = mtac.process(txn);


        // TODO: anticipate having several more rules here

        // Aggregate the results of the rules to get overall payment status
        // Since we only have one rule implemented, this is trivial
        // We could also weight results differently here if desired
        int fraudRisk = 0;
        switch (txnAmountRisk) {
            case HIGH: fraudRisk = 90; break;
            case MEDIUM: fraudRisk = 50; break;
            case LOW: fraudRisk = 5; break;
        }

        txn.setFraudResult(fraudRisk);
        entry.setValue(txn);  // update the transaction in the map with the result
        System.out.println("Fraud rules complete");
        return fraudRisk;
    }

    @Override
    public EntryBackupProcessor<String, Transaction> getBackupProcessor() {
        return null;
    }
}
