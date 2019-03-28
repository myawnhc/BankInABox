package com.theyawns.domain.payments;

import com.theyawns.ruleengine.HasID;

import java.io.Serializable;
import java.util.Date;

// TODO: IDS
public class Transaction implements Serializable, HasID {

    private String acctNumber;
    private String transactionId;

    private Date timestamp;
    private Double amount;
    private String vendor;
    private ReportedMobileLocation reportedMobileLocation; // Should this be a separate object, enrichment source?

    public int fraudResult = -1;
    public Boolean paymentResult;

    public Transaction(int num) {

        this.transactionId = ""+num;
    }

    public String getID() {
        return transactionId;
    }

    public void setAccountNumber(String acct) { this.acctNumber = acct; }
    public String getAccountNumber() {
        return acctNumber;
    }

    public void setAmount(Double amt) { this.amount = amt; }
    public Double getAmount() {
        return amount;
    }

    public void setFraudResult(int result) {
        fraudResult = result;
    }

    public int getFraudResult() {
        return fraudResult;
    }

    public Boolean getPaymentResult() {
        return paymentResult;
    }

    public void setPaymentResult(boolean result) {
        paymentResult = result;
    }

    @Override
    public String toString() {
        return "Transaction " + transactionId;
    }
}
