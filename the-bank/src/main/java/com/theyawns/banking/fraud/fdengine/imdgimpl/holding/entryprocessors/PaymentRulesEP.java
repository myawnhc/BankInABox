package com.theyawns.banking.fraud.fdengine.imdgimpl.holding.entryprocessors;

import com.hazelcast.map.EntryProcessor;
import com.theyawns.banking.Account;
import com.theyawns.banking.Transaction;

import java.io.Serializable;
import java.util.Map;

public class PaymentRulesEP implements EntryProcessor<String, Transaction, Boolean>, Serializable {

    private Account account;

    public void setAccount(Account acct) { this.account = acct; }

    @Override
    public Boolean process(Map.Entry<String, Transaction> entry) {
        Transaction txn = entry.getValue();
        //System.out.println("Processing PaymentRulesEP for " + txn.getID());

        // Run a credit limit check
        CreditLimitCheck clc = new CreditLimitCheck();
        clc.setAccount(account);
        boolean clcOK = clc.process(txn);

        // TODO: anticipate having a second rule here on account status

        // Aggregate the results of the rules to get overall payment status
        // Since we only have one rule implemented, this is trivial
        txn.setPaymentResult(clcOK);
        //System.out.println("Payment rules complete, end IMDG processing and record");


        //txn.processingTime.stop();
        entry.setValue(txn);  // update the transaction in the map with the result
        // Record now does TPS only, so call only at E2E completion
        //PerfMonitor.getInstance().recordTransaction("IMDG", txn);
        return clcOK;
    }

//    @Override
//    public EntryBackupProcessor<String, Transaction> getBackupProcessor() {
//        return null;
//    }
}
