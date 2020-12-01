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

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.hazelcast.map.IMap;
import com.theyawns.banking.Account;
import com.theyawns.banking.Merchant;
import com.theyawns.banking.Transaction;
import com.theyawns.controller.Constants;
import com.theyawns.controller.launcher.BankInABoxProperties;

import java.io.Serializable;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@Deprecated // Data is now pre-generated and stored to database
public class TransactionGenerator {

    private final static ILogger log = Logger.getLogger(TransactionGenerator.class);

    private int txnnum;

    private IMap<String, Account> accountMap; // Key = Account ID
    private IMap<String, Transaction> preAuthMap; // Key = Transaction ID
    private IMap<String, List<Transaction>> historyMap; // Key = Account ID;
    private IMap<String, Merchant> merchantMap;

    private HazelcastInstance hazelcast;
    private ExecutorService singleThreadExecutor;
    private ExecutorService threadPoolExecutor;

    private TransactionGeneratorHelper helper;
    private Random acctRandom;

    public void init(HazelcastInstance hz) {
        hazelcast = hz;
        helper = new TransactionGeneratorHelper(hazelcast);
        accountMap = hz.getMap(Constants.MAP_ACCOUNT);
        preAuthMap = hz.getMap(Constants.MAP_PREAUTH);
        merchantMap = hz.getMap(Constants.MAP_MERCHANT);
        //executor = hz.getExecutorService("dataLoader");
        singleThreadExecutor = Executors.newSingleThreadExecutor();
        threadPoolExecutor = Executors.newFixedThreadPool(BankInABoxProperties.TRANSACTION_THREADS);
        acctRandom = new Random(37);
    }

    interface DistributedCallable<T> extends Callable<T>, Serializable {}

    private class TransactionGenTask implements DistributedCallable<Integer> {
        private int generatorID;
        private int startingWith;
        private int count;

        public TransactionGenTask(int genid, int startingWith, int count) {
            this.generatorID = genid;
            this.startingWith = startingWith;
            this.count = count;
        }

        public Integer call() {
            log.info("Transaction generator " + generatorID + " starting at " + startingWith + " for " + count);
            for(int i = startingWith; i<startingWith+count;i++) {
                int acctNum = acctRandom.nextInt(BankInABoxProperties.ACCOUNT_COUNT);
                Account a = accountMap.get(TransactionGeneratorHelper.formatAccountId(acctNum));
                Transaction t = helper.generateTransactionForAccount(a, txnnum++);
                preAuthMap.set(t.getItemID(), t);
            }
            log.info("Transaction generator " + generatorID + " finished.");
            return count;
        }
    }

    public void start() throws InterruptedException {

//        log.info("Generating merchants");
//        DistributedCallable<Integer> merchantGenTask = () -> {
//            for (int i=0; i<BankInABoxProperties.MERCHANT_COUNT; i++) {
//                Merchant m = helper.generateNewMerchant(i);
//                merchantMap.set(m.getMerchantId(), m);
//            }
//            return merchantMap.size();
//        };
//        // Submit to executor and wait for completion
//        Future<Integer> future = singleThreadExecutor.submit(merchantGenTask);
//        try {
//            int count = future.get();
//            log.info("Generated " + count + " merchants");
//        } catch (ExecutionException e) {
//            e.printStackTrace();
//        }

//        System.out.println("Generating accounts");
//        DistributedCallable<Integer> accountGenTask = () -> {
//            for (int i=0; i<BankInABoxProperties.ACCOUNT_COUNT; i++) {
//                Account a = helper.generateNewAccount(i);
//                accountMap.set(a.getAccountNumber(), a);
//                // future: generate historical transactions, populate historyMap
//            }
//            return accountMap.size();
//        };
//        Future<Integer> acctFuture = singleThreadExecutor.submit(accountGenTask);
//        try {
//            int count = acctFuture.get();
//            log.info("Generated " + count + " accounts");
//        } catch (ExecutionException e) {
//            e.printStackTrace();
//        }

        log.info("Generating transactions");
        int threadCount = BankInABoxProperties.TRANSACTION_THREADS;
        DistributedCallable<Integer>[] transactionGenerators = new DistributedCallable[threadCount];
        Future<Integer>[] txnFutures = new Future[threadCount];
        int start = 0;
        int count = BankInABoxProperties.TRANSACTION_COUNT / BankInABoxProperties.TRANSACTION_THREADS;
        for (int i=0; i<threadCount; i++) {
            transactionGenerators[i] = new TransactionGenTask(i, start, count);
            start += count;
            txnFutures[i] = threadPoolExecutor.submit(transactionGenerators[i]);
        }
        threadPoolExecutor.shutdown();  // Accept no further submissions


        boolean normalTermination = false;
        int generated = 0;
        try {
            normalTermination = threadPoolExecutor.awaitTermination(1, TimeUnit.HOURS);
            for (Future<Integer> f : txnFutures) {
                generated += f.get();
            }
        } catch (InterruptedException e) {
            log.info("Interrupted waiting for transaction generation");
        } catch (ExecutionException e) {
            log.info("Execution exception fetching results");
            e.printStackTrace();
        } finally {

            log.info("Generated " + generated+ " transactions");
        }
        System.exit(0);
    }

    public static void main(String[] args) throws InterruptedException {
        HazelcastInstance hazelcast = HazelcastClient.newHazelcastClient();

        TransactionGenerator tgen = new TransactionGenerator();
        tgen.init(hazelcast);
        tgen.start();

    }

}
