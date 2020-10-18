package com.theyawns.banking.fraud.fdengine.imdgimpl.holding.entryprocessors;


import com.theyawns.banking.Account;
import com.theyawns.banking.Transaction;

import java.io.Serializable;

public class CreditLimitCheck implements Serializable {

    private Account account;

    public void setAccount(Account acct) {
        this.account = acct;
    }

    public Boolean process(Transaction txn) {
        if (account == null) {
            throw new IllegalStateException("Account must be set prior to invoking EntryProcessor");
        }

        boolean approved = true;
        double projectedBalance = account.getBalance() + txn.getAmount();
        if (projectedBalance > account.getCreditLimit())
            approved = false;

        return approved;
    }


}
