package com.theyawns.entryprocessors;

import com.hazelcast.map.EntryBackupProcessor;
import com.hazelcast.map.EntryProcessor;
import com.theyawns.domain.payments.Account;
import com.theyawns.domain.payments.Transaction;

import java.io.Serializable;
import java.util.Map;

public class PaymentRulesEP implements EntryProcessor<String, Transaction>, Serializable {

    private Account account;

    public void setAccount(Account acct) { this.account = acct; }

    @Override
    public Object process(Map.Entry<String, Transaction> entry) {
        //System.out.println("Processing PaymentRulesEP");
        Transaction txn = entry.getValue();

        // Run a credit limit check
        CreditLimitCheck clc = new CreditLimitCheck();
        clc.setAccount(account);
        boolean clcOK = clc.process(txn);

        // TODO: anticipate having a second rule here on account status

        // Aggregate the results of the rules to get overall payment status
        // Since we only have one rule implemented, this is trivial
        txn.setPaymentResult(clcOK);
        entry.setValue(txn);  // update the transaction in the map with the result
        //System.out.println("Payment rules complete");
        return clcOK;
    }

    @Override
    public EntryBackupProcessor<String, Transaction> getBackupProcessor() {
        return null;
    }
}
