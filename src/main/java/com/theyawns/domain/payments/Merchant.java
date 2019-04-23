package com.theyawns.domain.payments;

import java.io.Serializable;
import java.util.Random;

// TODO: IdentifiedDataSerializable, at least
public class Merchant implements Serializable {

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

    public String getId() { return merchantID; }

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
}
