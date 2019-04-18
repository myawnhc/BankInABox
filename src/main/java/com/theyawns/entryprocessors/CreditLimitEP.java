package com.theyawns.entryprocessors;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.map.EntryBackupProcessor;
import com.hazelcast.map.EntryProcessor;
import com.theyawns.domain.payments.Account;
import com.theyawns.domain.payments.Transaction;

import java.io.Serializable;
import java.util.Map;

public class CreditLimitEP implements EntryProcessor<String, Transaction>, Serializable {

    private transient HazelcastInstance hazelcast;

    @Override
    public Object process(Map.Entry<String, Transaction> entry) {
        System.out.println("CreditLimitEP: process");
        Transaction txn = entry.getValue();
        String id = txn.getID();
        String accountNum = txn.getAccountNumber();
        IMap<String, Account> amap = hazelcast.getMap("account");
        Account account = amap.get(accountNum);

        boolean approved = true;
        double projectedBalance = account.getBalance() + txn.getAmount();
        if (projectedBalance > account.getCreditLimit())
            approved = false;

        txn.setPaymentResult(approved);

        return txn;
    }

    @Override
    public EntryBackupProcessor<String, Transaction> getBackupProcessor() {
        return null;
    }

    public void setHazelcastInstance(HazelcastInstance hz) {
        hazelcast = hz;
    }
}
