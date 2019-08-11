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

    private int fraudResult = -1;
    private Boolean paymentResult = Boolean.TRUE;

    // Being a little sloppy with encapsulation, will allow direct access to these
//    public LatencyMetric processingTime = new LatencyMetric();
//    public LatencyMetric endToEndTime = new LatencyMetric();

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
        //this.timestamp = copyfrom.timestamp;
        this.amount = copyfrom.amount;
//        this.processingTime = copyfrom.processingTime;
//        this.endToEndTime = copyfrom.endToEndTime;
        // result fields won't be copied
    }

    public String getID() {
        return transactionId;
    }
    public void setID(String id) { transactionId = id; }

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

    @Override
    public String toString() {
        return "Transaction " + transactionId;
    }

    // IdentifiedDataSerializable implementation

    @Override
    public int getFactoryId() {
        return Constants.IDS_FACTORY_ID;
    }

    @Override
    public int getId() {
        return Constants.IDS_TRANSACTION_ID;
    }

    @Override
    public void writeData(ObjectDataOutput objectDataOutput) throws IOException {
        objectDataOutput.writeUTF(transactionId);
        objectDataOutput.writeUTF(acctNumber);
        objectDataOutput.writeUTF(merchantId);
        objectDataOutput.writeDouble(amount);
        objectDataOutput.writeUTF(location);
        objectDataOutput.writeInt(fraudResult);
        objectDataOutput.writeBoolean(paymentResult);
//        objectDataOutput.writeObject(processingTime);
//        objectDataOutput.writeObject(endToEndTime);
    }

    @Override
    public void readData(ObjectDataInput objectDataInput) throws IOException {
        transactionId = objectDataInput.readUTF();
        acctNumber = objectDataInput.readUTF();
        merchantId = objectDataInput.readUTF();
        amount = objectDataInput.readDouble();
        location = objectDataInput.readUTF();
        fraudResult = objectDataInput.readInt();
        paymentResult = objectDataInput.readBoolean();
//        processingTime = objectDataInput.readObject(LatencyMetric.class);
//        endToEndTime = objectDataInput.readObject(LatencyMetric.class);
    }
}
