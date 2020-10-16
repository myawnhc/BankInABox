package com.theyawns.domain.payments;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;
import com.theyawns.Constants;
import com.theyawns.ruleengine.HasID;

import java.io.IOException;
import java.io.Serializable;

public class Transaction implements IdentifiedDataSerializable, Serializable, HasID {

    private String transactionId;
    private String acctNumber;      // TODO: this won't be part of object eventually - txn id keys
    private String merchantId;

    //private long timestamp;
    private Double amount = 0.0;
    private String location; // Should this be a separate object, enrichment source?

    // These fields should all really be pulled out of Transaction and put
    // into some sort of wrapper / carrier object, so that it all passes
    // along through the processing stages but doesn't pollute the domain
    // object with temporary in-process state.
    private int fraudResult = -1;
    private Boolean paymentResult = Boolean.TRUE;
    private int numberOfRuleSetsThatApply;
    private long timeEnqueuedForRuleEngine; // nanotime
    private long timeEnqueuedForAggregator; // nanotime - not used
    private long timeSpentQueued; // sum of RE + Aggregator - not used

    // No-arg constructor for use by serialization
    public Transaction() {
        //timestamp = System.currentTimeMillis();
    }

    public Transaction(String txnId) {
        this.transactionId = txnId;
    }

    @Deprecated
    public Transaction(int num) {
        this();
        this.transactionId = ""+num;
    }

    public Transaction(Transaction copyfrom) {
        this();
        this.acctNumber = copyfrom.acctNumber;
        this.transactionId = copyfrom.transactionId;
        this.merchantId = copyfrom.merchantId;
        this.amount = copyfrom.amount;
    }

    public String getItemID() {
        return transactionId;
    }
    public void setItemID(String id) { transactionId = id; }

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

    public void setLocation(String l) { this.location = l; }
    public String getLocation() { return location; }

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

    public void setNumberOfRuleSetsThatApply(int count) { numberOfRuleSetsThatApply = count; }
    public int getNumberOfRuleSetsThatApply() { return numberOfRuleSetsThatApply; }

    //public void setTimeEnqueuedForRuleEngine(long time) { timeEnqueuedForRuleEngine = time; }
    public void setTimeEnqueuedForRuleEngine() { timeEnqueuedForRuleEngine = System.nanoTime(); }
    public long getTimeEnqueuedForRuleEngine() { return timeEnqueuedForRuleEngine; }

    public void setTimeEnqueuedForAggregator() { timeEnqueuedForAggregator = System.nanoTime(); }
    public long getTimeEnqueuedForAggregator() { return timeEnqueuedForAggregator; }

    public void addToQueueWaitTime(long value) {
        timeSpentQueued += value;
    }
    public long getQueueWaitTime() {
        return timeSpentQueued;
    }
    @Override
    public String toString() {
        return "Transaction " + transactionId + " account " + acctNumber + " merchant " + merchantId + " amount " + amount;
    }

    // IdentifiedDataSerializable implementation

    //@Override
    public int getFactoryId() {
        return Constants.IDS_FACTORY_ID;
    }

    //@Override
    public int getClassId() {
        return Constants.IDS_TRANSACTION_ID;
    }

    //@Override
    public void writeData(ObjectDataOutput objectDataOutput) throws IOException {
        objectDataOutput.writeUTF(transactionId);
        objectDataOutput.writeUTF(acctNumber);
        objectDataOutput.writeUTF(merchantId);
        objectDataOutput.writeDouble(amount);
        objectDataOutput.writeUTF(location);
        objectDataOutput.writeInt(fraudResult);
        objectDataOutput.writeBoolean(paymentResult);
        objectDataOutput.writeInt(numberOfRuleSetsThatApply);
        objectDataOutput.writeLong(timeEnqueuedForRuleEngine);
        objectDataOutput.writeLong(timeEnqueuedForAggregator);
        objectDataOutput.writeLong(timeSpentQueued);
    }

    //@Override
    public void readData(ObjectDataInput objectDataInput) throws IOException {
        transactionId = objectDataInput.readUTF();
        acctNumber = objectDataInput.readUTF();
        merchantId = objectDataInput.readUTF();
        amount = objectDataInput.readDouble();
        location = objectDataInput.readUTF();
        fraudResult = objectDataInput.readInt();
        paymentResult = objectDataInput.readBoolean();
        numberOfRuleSetsThatApply = objectDataInput.readInt();
        timeEnqueuedForRuleEngine = objectDataInput.readLong();
        timeEnqueuedForAggregator = objectDataInput.readLong();
        timeSpentQueued = objectDataInput.readLong();
    }
}
