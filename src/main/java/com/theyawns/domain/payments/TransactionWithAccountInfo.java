package com.theyawns.domain.payments;

public class TransactionWithAccountInfo extends TransactionWithRules {

    protected Account enrichedAccount;


    public TransactionWithAccountInfo(TransactionWithRules twr) {
        super(twr, twr.rules);
    }

    public Account getAccountInfo() { return enrichedAccount; }
    public void setAccountInfo(Account account) { enrichedAccount = account; }
}
