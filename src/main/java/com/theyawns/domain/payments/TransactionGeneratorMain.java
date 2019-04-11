package com.theyawns.domain.payments;

import com.hazelcast.config.Config;
import com.hazelcast.config.ManagementCenterConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

public class TransactionGeneratorMain {

    public static void main(String[] args) throws InterruptedException {

        // Configure connection to management center so we can monitor pending transactions
        ManagementCenterConfig mcc = new ManagementCenterConfig();
        mcc.setEnabled(true);
        mcc.setUrl("http://localhost:8080/hazelcast-mancenter");

        // Embedded Hazelcast instance will hold 3 maps
        // - Account Map - Holds Account objects, keyed by Account ID
        // - History Map - will add for Fraud Detection later, will have List<Transaction> keyed by Account ID
        // - Pending Transactions map - will have Transactions for which rules will be run, added by
        //      transaction generator and then removed by EntryListener when Jet results are posted.
        Config hzConfig = new Config();
        hzConfig.getGroupConfig().setName("dev").setPassword("ignored");
        hzConfig.getMapEventJournalConfig("preAuth").setEnabled(true).setCapacity(1000000);
        hzConfig.setManagementCenterConfig(mcc);
        hzConfig.setProperty("hazelcast.map.entry.filtering.natural.event.types", "true");
        System.out.println("Starting IMDG instance for Transaction Generator");
        HazelcastInstance embeddedHZ = Hazelcast.newHazelcastInstance(hzConfig);

        TransactionGenerator tgen = new TransactionGenerator();
        tgen.init(embeddedHZ);
        tgen.start();

        TransactionMonitor tmon = new TransactionMonitor(embeddedHZ);
        new Thread(tmon).start();


        // Currently generator will self-stop after creating 1 million transactions.

    }
}
