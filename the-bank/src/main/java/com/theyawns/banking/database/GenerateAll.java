package com.theyawns.banking.database;

import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.theyawns.controller.launcher.BankInABoxProperties;
import com.theyawns.controller.config.EnvironmentSetup;

import java.util.concurrent.CompletableFuture;

// Replacement for TransactionGenerator; runs in advance (rather than at demo time)
// and writes all items to a database
public class GenerateAll {

    private final static ILogger log = Logger.getLogger(GenerateAll.class);

    public static void main(String[] args) {
    	new EnvironmentSetup();
    	BankInABoxDB database = new BankInABoxDB();
        database.establishConnection(); // connects to server, in a non-db-specific way
        database.createDatabase();

        /////////////// Merchants
        MerchantTable merchantTable = new MerchantTable();
        merchantTable.establishConnection();   // Connects to BankInABox specifically
        merchantTable.createMerchantTable();

        log.info("Generating merchants");
        CompletableFuture<Void> merchantFuture = CompletableFuture.runAsync(() -> {
            int c = merchantTable.generateAndStoreMultiple(BankInABoxProperties.MERCHANT_COUNT);
            log.info("Generated " + c + " merchants");
        });


        /////////////// Accounts
        AccountTable accountTable = new AccountTable();
        accountTable.establishConnection();
        accountTable.createAccountTable();

        log.info("Generating accounts");
        CompletableFuture<Void> accountFuture = CompletableFuture.runAsync(() -> {
            int c = accountTable.generateAndStoreMultiple(BankInABoxProperties.ACCOUNT_COUNT);
            log.info("Generated " + c + " accounts");
        });

        /////////////// Transactions
        TransactionTable transactionTable = new TransactionTable();
        transactionTable.establishConnection();
        transactionTable.createTransactionTable();

        log.info("Generating transactions");
        CompletableFuture<Void> transactionFuture = CompletableFuture.runAsync(() -> {
            int c = transactionTable.generateAndStoreMultiple(BankInABoxProperties.TRANSACTION_COUNT);
            log.info("Generated " + c + " transactions");
        });

        log.info("All launched, waiting on completion");
        CompletableFuture<Void> all = CompletableFuture.allOf(merchantFuture, accountFuture, transactionFuture);
        try {
            all.get();
        } catch (Exception e) {
            e.printStackTrace();
        }
        log.info("All complete.");
        System.exit(0);
    }
}
