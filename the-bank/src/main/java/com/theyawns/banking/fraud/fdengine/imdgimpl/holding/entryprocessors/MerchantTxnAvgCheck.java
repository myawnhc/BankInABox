package com.theyawns.banking.fraud.fdengine.imdgimpl.holding.entryprocessors;

import com.theyawns.banking.Merchant;
import com.theyawns.banking.Transaction;

import java.io.Serializable;

public class MerchantTxnAvgCheck implements Serializable {

    private Merchant merchant;

    public void setMerchant(Merchant merc) {
        this.merchant = merc;
    }

    public Merchant.RISK process(Transaction txn) {
        if (merchant == null) {
            throw new IllegalStateException("Merchant must be set prior to invoking EntryProcessor");
        }

        Double amount = txn.getAmount();
        return merchant.transactionAmountFraudRisk(amount);
    }
}
