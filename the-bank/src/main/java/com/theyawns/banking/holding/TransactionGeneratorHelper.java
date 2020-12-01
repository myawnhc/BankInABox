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

package com.theyawns.banking.holding;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.theyawns.banking.Account;
import com.theyawns.banking.Merchant;
import com.theyawns.banking.Transaction;
import com.theyawns.controller.Constants;
import com.theyawns.controller.launcher.BankInABoxProperties;

import java.text.DecimalFormat;
import java.util.Random;

@Deprecated // data is now pre-generated and stored to database
public class TransactionGeneratorHelper {

    private Random countryCodeRandom;
    private Random cityCodeRandom;
    private Random merchantRandom;
    private Random txnAmountRandom;
    private Random locationRandom;
    private Random accountRandom;
    private Random responseCodeRandom;

    private HazelcastInstance hazelcast;

    IMap<String, Merchant> merchantMap;

    private static final DecimalFormat merchantFormat = new DecimalFormat("00000000");       // 8 digit
    private static final DecimalFormat accountFormat  = new DecimalFormat( "0000000000");    // 10 digit
    private static final DecimalFormat txnFormat      = new DecimalFormat("00000000000000"); // 14 digit

    public static String formatMerchantId(int id) {
        return merchantFormat.format(id);
    }

    public static String formatAccountId(int id) {
        return accountFormat.format(id);
    }

    public TransactionGeneratorHelper(HazelcastInstance hazelcast) {
        this.hazelcast = hazelcast;
        //countryCodeRandom = new Random(1);
        //cityCodeRandom = new Random(1);
        merchantRandom = new Random(1);
        txnAmountRandom = new Random(100);
        locationRandom = new Random(42);
        accountRandom = new Random(12345);
        //responseCodeRandom = new Random(10);

        merchantMap = hazelcast.getMap(Constants.MAP_MERCHANT);
    }

    public Account generateNewAccount(int acctNum) {
        String accountID = formatAccountId(acctNum);
        Account acct = new Account(accountID);
        //acct.setLastReportedLocation(Location.getRandom());
        return acct;
    }

    public Transaction generateTransactionForAccount(Account a, int txnNum) {
        Transaction t = new Transaction(txnNum);
        t.setAccountNumber(a.getAccountNumber());

        int id = merchantRandom.nextInt(BankInABoxProperties.MERCHANT_COUNT);
        String merchantID = formatMerchantId(id);
        t.setMerchantId(merchantID);

        // Transaction amounts will be normally distributed around vendor average
        Merchant merchant = merchantMap.get(merchantID);
        t.setAmount(merchant.getRandomTransactionAmount());

        // 75% of transactions will be in same city as last reported location
//        int value = locationRandom.nextInt(100);
//        if (value < 75) {
//            t.setLocation(a.getLastReportedLocation());
//        }
//        // 20% will be in a close-by city
//        else if (value < 95) {
//            t.setLocation(a.getLastReportedLocation().getCloseCity());
//        }
//        // 5% will be random
//        else t.setLocation(Location.getRandom());

        return t;
    }

    public Merchant generateNewMerchant(int merchantId) {
        Merchant m = new Merchant(merchantFormat.format(merchantId));
        // TODO: set location,
        return m;
    }

//    /**
//     * Create dummy transaction for the given credit card number
//     *
//     * @param creditCardNumber
//     *            Credit Card number for which the transactions to be created
//     * @param code Transaction Code which is
//     * @return concatenated Transaction String made of all variables separated by comma (,)
//     */
//    public String createAndGetCreditCardTransaction(String creditCardNumber, int code) {
//
//        StringBuffer txn = new StringBuffer();
//        String countryCode= generateCountryCode();
//
//        txn.append(creditCardNumber)
//                .append(",").append(generateTimeStamp())
//                .append(",").append(countryCode)
//                .append(",").append(generateResponseCode(code))
//                .append(",").append(generateTxnAmount())
//                .append(",").append(countryCode)
//                .append(",").append(generateMerchantType())
//                .append(",").append(generateCityCode())
//                .append(",").append(generateTxnCode(code))
//                .append(new String(new byte[99 - txn.toString().getBytes().length]))
//                .append("\n");
//
//        return txn.toString();
//    }

//    /**
//     * Create dummy transactions for the given credit card number
//     *
//     * @param creditCardNumber
//     *            Credit Card number for which the transactions to be created
//     * @param txnCount
//     *            number of historical transactions to create
//     * @return List of transactions for a credit card
//     */
//    public List<Transaction> createAndGetCreditCardTransactions(
//            String creditCardNumber, int txnCount) {
//        List<Transaction> transactions = new ArrayList<Transaction>();
//        for (int j = 0; j < txnCount; j++) {
//
//            Transaction txn = new Transaction();
//            String countryCode = generateCountryCode();
//            txn.setCreditCardNumber(creditCardNumber);
//            txn.setTimeStamp(generateTimeStamp());
//            txn.setCountryCode(countryCode);
//            txn.setResponseCode(generateResponseCode(j));
//            txn.setTxnAmt(generateTxnAmount());
//            // Currency is same as CountryCode
//            txn.setTxnCurrency(countryCode);
//            txn.setMerchantType(generateMerchantType());
//            txn.setTxnCity(generateCityCode());
//            txn.setTxnCode(generateTxnCode(j));
//
//            transactions.add(txn);
//        }
//        return transactions;
//    }




//    // last 90 days
//    public long generateHistoricalTimeStamp() {
//        long offset = Date.now().getMillis();
//        long end = DateTime.now().minusDays(90).getMillis();
//        long diff = end - offset + 1;
//        return offset + (long) (Math.random() * diff);
//    }



    // 100-50000 random
    public Double generateTxnAmount() {
        return txnAmountRandom.nextDouble()*5000+1; // Range to $5000
    }

}
