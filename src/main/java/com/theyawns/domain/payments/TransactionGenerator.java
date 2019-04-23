package com.theyawns.domain.payments;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.core.IQueue;

import java.io.Serializable;
import java.util.List;
import java.util.concurrent.*;

public class TransactionGenerator {

    // Initialize with a Queue?
    private transient boolean active = false;
    private int acctNum;
    private int txnnum;
    private IQueue<Transaction> queue;
    private IMap<String,Account> accountIMap; // Key = Account ID
    private IMap<String,Transaction> preAuthMap; // Key = Transaction ID
    private IMap<String, List<Transaction>> historyMap; // Key = Account ID;
    private IMap<String, Merchant> merchantMap;

    private HazelcastInstance hazelcast;
    private ExecutorService executor;

    // have value for # of concurrent transaction generator threads to run

    // have some tracking of TPS generation rate and queue length ... perhaps back off when queue is full ?

    // Maybe try to optimize - take queue size / 2, every so often check, if queue < 25% full, create another
    // generator; if queue > 75% queue, stop a generator.

    public void init(HazelcastInstance hz) {
        hazelcast = hz;
        accountIMap = hz.getMap("accountMap");
        // TODO: history IMap (future, for use by fraud detection rules)
        preAuthMap = hz.getMap("preAuth");
        merchantMap = hz.getMap("merchantMap");
        //executor = hz.getExecutorService("dataLoader");
        executor = Executors.newSingleThreadScheduledExecutor();
    }

    @Deprecated
    public void init(IMap<String,Account> map, IQueue<Transaction> queue) {
        this.queue = queue;
        this.accountIMap = map;
    }

    interface DistributedCallable<T> extends Callable<T>, Serializable {}

    public void start() throws InterruptedException {
        TransactionGeneratorHelper helper = new TransactionGeneratorHelper(hazelcast);

        System.out.println("Generating merchants");
        // TODO: this and task below must be serializable
        DistributedCallable<Integer> merchantGenTask = () -> {
            for (int i=0; i<10000; i++) {
                Merchant m = helper.generateNewMerchant(i);
                merchantMap.put(m.getId(), m);
            }
            return merchantMap.size();
        };
        // Submit to executor and wait for completion
        Future<Integer> future = executor.submit(merchantGenTask);
        try {
            int count = future.get();
            System.out.println("Generated " + count + " merchants");
        } catch (ExecutionException e) {
            e.printStackTrace();
        }


        System.out.println("Generating accounts and transactions");
        DistributedCallable<Integer> txnGenTask = () -> {
            active = true;
            while (active) {
                Account a = helper.generateNewAccount(acctNum++);
                accountIMap.put(a.getAccountNumber(), a);

                // TODO: generate historical transactions, populate historyMap

                Transaction t = helper.generateTransactionForAccount(a, txnnum++);

                preAuthMap.set(t.getID(), t);

                // TODO: add entry listener on resultsMap

                //queue.add(t);
                if (txnnum % 10000 == 0) {
                    System.out.println("Added " + txnnum + " transactions, pending size " + preAuthMap.size());
                    if (txnnum >= 1000000) {
                        // Memory constraint, let's not do more than 1 million.  Also, run timer was removed so this is the only constraint now.
                        System.out.println("TxnGen stopping before timer expired due to size (1 million)");
                        active = false;
                    }
                }

                // Remove at scale, but on laptop combined workload is making
                // IDE unresponsive.
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            System.out.println("Stopped transaction generation, pending size now " + preAuthMap.size() );
            return txnnum;
        };
        future = executor.submit(txnGenTask);
        try {
            int count = future.get();
            System.out.println("Generated " + count + " transactions");
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        System.exit(0);
    }

    // Not in use - was previously set by timer, now we exit after 1 million transactions generated
    public void stop() {
        active = false;
        //System.out.println("Stopping");
    }
}
