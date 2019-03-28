package com.theyawns.domain.payments;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.core.IQueue;

import java.util.List;

public class TransactionGenerator {

    // Initialize with a Queue?
    private transient boolean active = false;
    private int acctNum;
    private int txnnum;
    private IQueue<Transaction> queue;
    private IMap<String,Account> accountIMap; // Key = Account ID
    private IMap<String,Transaction> pendingTransactionsMap; // Key = Transaction ID
    private IMap<String, List<Transaction>> historyMap; // Key = Account ID;

    private HazelcastInstance hazelcast;

    // have value for # of concurrent transaction generator threads to run

    // have some tracking of TPS generation rate and queue length ... perhaps back off when queue is full ?

    // Maybe try to optimize - take queue size / 2, every so often check, if queue < 25% full, create another
    // generator; if queue > 75% queue, stop a generator.

    public void init(HazelcastInstance hz) {
        hazelcast = hz;
        accountIMap = hz.getMap("accountMap");
        // TODO: history IMap (future, for use by fraud detection rules)
        pendingTransactionsMap = hz.getMap("pendingTransactions");
    }

    @Deprecated
    public void init(IMap<String,Account> map, IQueue<Transaction> queue) {
        this.queue = queue;
        this.accountIMap = map;
    }

    public void start() throws InterruptedException {
        System.out.println("Starting transaction generator");
        GeneratorHelper helper = new GeneratorHelper();
        Runnable task = () -> {
            active = true;
            while (active) {
                Account a = helper.generateNewAccount(acctNum++);
                accountIMap.put(a.getAccountNumber(), a);

                // TODO: generate historical transactions, populate historyMap

                Transaction t = helper.generateTransactionForAccount(a, txnnum++);

                pendingTransactionsMap.set(t.getID(), t);

                // TODO: add entry listener on resultsMap

                //queue.add(t);
                if (txnnum % 10000 == 0) {
                    System.out.println("Added " + txnnum + " transactions, pending size " + pendingTransactionsMap.size());
                    if (txnnum >= 1000000) {
                        // Memory constraint, let's not do more than 1 million.  Also, run timer was removed so this is the only constraint now.
                        System.out.println("TxnGen stopping before timer expired due to size (1 million)");
                        active = false;
                    }
                }
            }
            System.out.println("Stopped transaction generation, pending size now " + pendingTransactionsMap.size() );

        };
        new Thread(task).start();


    }

    // Not in use - was previously set by timer, now we exit after 1 million transactions generated
    public void stop() {
        active = false;
        //System.out.println("Stopping");
    }
}
