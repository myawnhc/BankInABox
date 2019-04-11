package com.theyawns.domain.payments;

import com.hazelcast.core.EntryEvent;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.map.listener.EntryUpdatedListener;
import com.hazelcast.query.SqlPredicate;

public class TransactionMonitor implements Runnable,
        //EntryAddedListener<String, Transaction>,
        EntryUpdatedListener<String, Transaction> {
        //EntryRemovedListener<String, Transaction> {

    private HazelcastInstance hazelcast;
    private IMap<String,Transaction> preAuthMap;
    private IMap<String,Transaction> approved;
    private IMap<String,Transaction> rejected;

    public TransactionMonitor(HazelcastInstance instance) {
        hazelcast = instance;
    }


//    @Override
//    public void entryAdded(EntryEvent<String, Transaction> entryEvent) {
//        System.out.println("Added " + entryEvent);
//    }
//
//    @Override
//    public void entryRemoved(EntryEvent<String, Transaction> entryEvent) {
//        System.out.println("Removed " + entryEvent);
//
//    }

    @Override
    public void entryUpdated(EntryEvent<String, Transaction> entryEvent) {
        // TODO: figure out why 'original' has the new value, and 'update' is null.
        String key = entryEvent.getKey();
        Transaction original = entryEvent.getOldValue();
        Transaction update = entryEvent.getMergingValue();
        boolean passed = original.getPaymentResult();
        preAuthMap.remove(key);
        System.out.println("Updated " + key + " payment " + (passed ? "approved" : "rejected") + " pending count " + preAuthMap.size());
        //System.out.println("original " + original + " result " + original.getPaymentResult());
        if (passed)
            approved.set(key, original);
        else
            rejected.set(key, original);

        if (update != null) {
            System.out.println("update " + update + " result " + update.getPaymentResult());
            preAuthMap.remove(update.getID());
            System.out.println("Updated, pending size " + preAuthMap.size());
        }

    }

    @Override
    public void run() {
        preAuthMap = hazelcast.getMap("preAuth");
        // Intent is for grafana to graph these ...
        approved = hazelcast.getMap("approved");
        rejected = hazelcast.getMap("rejected");
        preAuthMap.addEntryListener(this, new SqlPredicate("paymentResult != null"), true);
        System.out.println("EntryListener registered");
    }
}


