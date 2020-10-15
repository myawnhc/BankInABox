package com.theyawns.domain.payments;

import com.hazelcast.partition.PartitionAware;

import java.io.Serializable;

public class TransactionKey implements PartitionAware, Serializable {
    public String transactionID;
    public String accountID;
    public TransactionKey(String transactionID, String accountID) {
        this.transactionID = transactionID;
        this.accountID = accountID;
    }

    @Override
    public String getPartitionKey() {
        return accountID;
    }
}
