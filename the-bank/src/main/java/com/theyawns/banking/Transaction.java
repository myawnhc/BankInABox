/*
 *  Copyright 2018-2021 Hazelcast, Inc
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.package com.theyawns.controller.launcher;
 */

package com.theyawns.banking;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;
import com.theyawns.controller.Constants;
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
//    private int numberOfRuleSetsThatApply;
//    private long timeEnqueuedForRuleEngine; // millis
//    private long timeEnqueuedForAggregator; // millis
//    private long timeSpentQueued; // sum of RE + Aggregator - not used

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
//        objectDataOutput.writeInt(numberOfRuleSetsThatApply);
//        objectDataOutput.writeLong(timeEnqueuedForRuleEngine);
//        objectDataOutput.writeLong(timeEnqueuedForAggregator);
//        objectDataOutput.writeLong(timeSpentQueued);
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
//        numberOfRuleSetsThatApply = objectDataInput.readInt();
//        timeEnqueuedForRuleEngine = objectDataInput.readLong();
//        timeEnqueuedForAggregator = objectDataInput.readLong();
//        timeSpentQueued = objectDataInput.readLong();
    }
}
