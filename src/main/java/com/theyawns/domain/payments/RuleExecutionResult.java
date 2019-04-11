package com.theyawns.domain.payments;

public class RuleExecutionResult {

    Transaction transaction;
    String ruleName;
    long elapsedTime;
    boolean result;

    public RuleExecutionResult(Transaction txn, String ruleName) {
        this.transaction = txn;
        this.ruleName = ruleName;
    }

    public void setElapsed(long timeInMillis) {
        elapsedTime = timeInMillis;
    }

    public void setResult(boolean result) {
        this.result = result;
    }

    public String getTransactionID() { return transaction.getID(); }
}
