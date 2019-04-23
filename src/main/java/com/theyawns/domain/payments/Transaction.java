package com.theyawns.domain.payments;

import com.theyawns.ruleengine.HasID;

import java.io.Serializable;

// TODO: IDS
public class Transaction implements Serializable, HasID {

    private String acctNumber;      // TODO: this won't be part of object eventually - txn id keys
    private String transactionId;   // 
    private String merchantId;

    private long timestamp;
    private Double amount;
    private Location location; // Should this be a separate object, enrichment source?

    public int fraudResult = -1;
    public Boolean paymentResult;

    public Transaction(int num) {
        this.transactionId = ""+num;
        timestamp = System.currentTimeMillis();
    }

    public Transaction(Transaction copyfrom) {
        this.acctNumber = copyfrom.acctNumber;
        this.transactionId = copyfrom.transactionId;
        this.merchantId = copyfrom.merchantId;
        this.timestamp = copyfrom.timestamp;
        this.amount = copyfrom.amount;
        // results won't be copied

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

    public void setMerchantId(String id) {
        this.merchantId = id;
    }
    public String getMerchantId() { return merchantId; }

    public void setLocation(Location l) { this.location = l; }
    public Location getLocation() { return location; }

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

    /**
     * The time (at UTC) that the trnsaction was received
     */
    private long requestTime;
    public long getRequestTime() { return requestTime; }
    public void setRequestTime(long requestTime) { this.requestTime = requestTime; }


    @Override
    public String toString() {
        return "Transaction " + transactionId;
    }
}
