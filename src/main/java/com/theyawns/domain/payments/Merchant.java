package com.theyawns.domain.payments;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.IdentifiedDataSerializable;
import com.theyawns.Constants;

import java.io.IOException;
import java.io.Serializable;
import java.util.Random;

/* Must continue to support default Java Serializable until EntryProcessors implement IdentifiedDataSerializable */

public class Merchant implements IdentifiedDataSerializable, Serializable {

    private String merchantID;
    private String merchantName;
    private int reputation;   // range 1-10
    private double avgTxnAmount;
    private Location location;

    private Random random = new Random();

    public Merchant(String merchantID) {
        this.merchantID = merchantID;
        this.merchantName = "Merchant" + merchantID;
        // Randomly assign a price point to the vendor
        avgTxnAmount = pricePoints[random.nextInt(4)];
        // Randomly assign a reputation risk category
        reputation = random.nextInt(10);
        location = Location.getRandom();
    }

    // For IDS Serialization only
    public Merchant() {}

    // Updated by Jet
    public Double getAvgTxnAmount() { return avgTxnAmount; }
    public void setAvgTxnAmount(Double newValue) { avgTxnAmount = newValue; }



    public String getMerchantId() { return merchantID; }
    public Merchant getObject() { return this; }

    // Stuff related to average price for merchant.

    static final int[] pricePoints = new int[] { 10, 25, 50, 100, 500, 1000 };

    // Not truly random .. will be normally distributed around
    public double getRandomTransactionAmount() {
        int stddev = (int) avgTxnAmount / 5;
        double amount = random.nextGaussian() * stddev + avgTxnAmount;
        return amount;
    }



    public static enum RISK { LOW, MEDIUM, HIGH }

    public RISK transactionAmountFraudRisk(double amount) {
        int stddev = (int) avgTxnAmount / 5;
        // Roughly 70% of transactions should be within 1 std deviation
        if (amount >= avgTxnAmount-stddev && amount <= avgTxnAmount+stddev)
            return RISK.LOW;
        // Roughly 95% of transactions should be within 2 std deviations
        else if (amount >= avgTxnAmount-2*stddev && amount <= avgTxnAmount+2*stddev)
            return RISK.MEDIUM;
        // Over 99% of transactions should be within 3 - currently treating everything
        // outside of 2 std deviations the same
        else
            return RISK.HIGH;
    }

    public RISK reputationFraudRisk() {
        switch (reputation) {
            case 8: case 9: case 10:         return RISK.LOW;
            case 4: case 5: case 6:  case 7: return RISK.MEDIUM;
            case 0: case 1: case 2:  case 3: return RISK.HIGH;
        }
        // should not happen
        return RISK.MEDIUM;
    }

    public String toString() {
        return "Merchant " + merchantID + " avgTxn " + avgTxnAmount;
    }

    @Override
    public int getFactoryId() {
        return Constants.IDS_FACTORY_ID;
    }

    @Override
    public int getId() {
        return Constants.IDS_MERCHANT_ID;
    }

    @Override
    public void writeData(ObjectDataOutput objectDataOutput) throws IOException {
        objectDataOutput.writeUTF(merchantID);
        objectDataOutput.writeUTF(merchantName);
        objectDataOutput.writeInt(reputation);
        objectDataOutput.writeDouble(avgTxnAmount);
        objectDataOutput.writeObject(location);
    }

    @Override
    public void readData(ObjectDataInput objectDataInput) throws IOException {
        merchantID = objectDataInput.readUTF();
        merchantName = objectDataInput.readUTF();
        reputation = objectDataInput.readInt();
        avgTxnAmount = objectDataInput.readDouble();
        location = objectDataInput.readObject(Location.class);
    }
}
