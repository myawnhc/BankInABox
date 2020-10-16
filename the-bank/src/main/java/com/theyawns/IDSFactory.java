package com.theyawns;

import com.hazelcast.nio.serialization.DataSerializableFactory;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;
import com.theyawns.domain.payments.Account;
import com.theyawns.domain.payments.Merchant;
import com.theyawns.domain.payments.Transaction;

import java.io.Serializable;

public class IDSFactory implements DataSerializableFactory, Serializable {

    @Override
    public IdentifiedDataSerializable create(int id) {
        switch(id) {
            case Constants.IDS_TRANSACTION_ID: return new Transaction();
//            case Constants.IDS_TXN_WITH_ACCT:  return new TransactionWithAccountInfo();
//            case Constants.IDS_TXN_WITH_RULES: return new TransactionWithRules();
//            case Constants.IDS_LATENCY_METRIC: return new LatencyMetric();
            case Constants.IDS_ACCOUNT_ID:     return new Account();
            case Constants.IDS_MERCHANT_ID:    return new Merchant();
//            case Constants.IDS_LOCATION:       return new Location();
        }
        throw new IllegalArgumentException("Missing constructor invocation for type in IDSFactory");
    }
}