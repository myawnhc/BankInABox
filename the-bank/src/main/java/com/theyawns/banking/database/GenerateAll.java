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
