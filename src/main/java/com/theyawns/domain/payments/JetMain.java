package com.theyawns.domain.payments;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.config.Config;
import com.hazelcast.config.ManagementCenterConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.jet.Jet;
import com.hazelcast.jet.JetInstance;
import com.hazelcast.jet.config.JetConfig;
import com.theyawns.ruleengine.JetRuleEngine;
import com.theyawns.ruleengine.RuleSetFactory;

public class JetMain {

    private static transient boolean active = false;
    private static final int RUN_DURATION_MINUTES = 1;

    public static final String IMDG_HOST = "localhost:5701";


    public static void main(String[] args) throws InterruptedException {

        ManagementCenterConfig mcc = new ManagementCenterConfig();
        mcc.setEnabled(true);
        mcc.setUrl("http://localhost:8080/hazelcast-mancenter");

        // NEW: Embedded Hazelcast instance will hold 3 maps
        // - Account Map - Holds Account objects, keyed by Account ID
        // - History Map - will add for Fraud Detection later, will have List<Transaction> keyed by Account ID
        // - Pending Transactions map - will have Transactions for which rules will be run, added by
        //      transaction generator and then removed by EntryListener when Jet results are posted.
        ClientConfig hzConfig = new ClientConfig();
        hzConfig.getGroupConfig().setName("dev").setPassword("ignored");
        hzConfig.getNetworkConfig().addAddress(IMDG_HOST);
//      hzConfig.getMapEventJournalConfig("pendingTransactions").setEnabled(true); // Doing this on the IMDG/TxnGen side
//      hzConfig.setManagementCenterConfig(mcc);// Doing this on the IMDG/TxnGen side
        HazelcastInstance embeddedHZ = HazelcastClient.newHazelcastClient(hzConfig);

        // Note that we're running using the embedded Hazelcast instance for the domain objects as well as Jet;
        // if we wanted to use an external IMDG cluster we'd just change the following line
        JetConfig jc = new JetConfig();
        Config hcc = jc.getHazelcastConfig();
        // Avoid collision between the external IMDG (remoteMap) and the internal IMDG
        hcc.getNetworkConfig().setPort(5710); // Group name defaults to Jet but port still defaults to 5701
        hcc.setManagementCenterConfig(mcc);

        jc.setHazelcastConfig(hcc);
        System.out.println("Starting Jet instance"); // TODO: should defer this until we're ready to run the job!
        JetInstance jet = Jet.newJetInstance(jc);

        // Factory handles both the Payments and Fraud rulesets
        RuleSetFactory rsFactory = new TransactionRuleSetsFactory();

        JetRuleEngine<Transaction> engine = new JetRuleEngine<Transaction>(jet, hzConfig, rsFactory);
        //engine.init(ruleSet);

        // Transaction Queue is written to by TransactionGenerator, read by RuleEngine implementation
//        IQueue<Transaction> transactionQueue = hzWithinJet.getQueue("transactionQueue");
//        IMap<String, Account> accountMap = hzWithinJet.getMap("accountMap");

//        TransactionGenerator tgen = new TransactionGenerator();
//        //tgen.init(accountMap, transactionQueue);
//        tgen.init(embeddedHZ);
//        tgen.start();

        engine.run();

//        engine.startRuleTaskCreation();
//        Thread.sleep(1000); // Evaluation will stop if nothing queued so give creation a slight head start
//        engine.startRuleEvaluation();

        System.out.println("Starting timer");

        // For the IMDG version of this, we (the client) will read Transactions from the queue and process them.
        final int RUN_DURATION_MILLIS = RUN_DURATION_MINUTES * 60 * 1000;
        Runnable timer = () -> {
            try {
                Thread.sleep(RUN_DURATION_MILLIS);
            } catch (InterruptedException ie) {
            } finally {
                active = false;
            }
        };
        active = true;
        timer.run();

        // Process until the timer expires
//        while (active) {
//            System.out.println("Running");
//            Transaction txn = transactionQueue.take();
//            // TODO: will be a Future, will need to wait on it
//            RuleEvaluationResult ruleEvaluationResult = engine.process(txn);
//        }

        System.out.println("---- time expired ----");


        // Stop the transaction generator
        //tgen.stop();
    }

        // TODO: stop the rule engine cleanly, (wait for it to finish processing)
}
