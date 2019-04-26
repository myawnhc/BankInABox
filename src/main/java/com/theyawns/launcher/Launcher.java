package com.theyawns.launcher;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.config.Config;
import com.hazelcast.config.ManagementCenterConfig;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.jet.config.JetConfig;
import com.theyawns.domain.payments.CreditLimitRule;
import com.theyawns.domain.payments.Transaction;
import com.theyawns.listeners.TransactionMapListener;
import com.theyawns.perfmon.PerfMonitor;
import com.theyawns.pipelines.AdjustMerchantTransactionAverage;

import java.io.Serializable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Launcher {

    // TODO: control this from command line switch or other mechanism not requiring a rebuild!
    public static final Boolean COLLECT_PERFORMANCE_STATS = true;

    public static final String IMDG_HOST = "localhost:5701";
    protected ClientConfig ccfg;
    protected JetConfig jc;

    //protected static final int SINK_PORT = 2004;
    protected static String SINK_HOST;

    protected HazelcastInstance hazelcast;
    protected ExecutorService distributedES;

    static {
        System.setProperty("hazelcast.multicast.group", "228.19.18.20");
        SINK_HOST = System.getProperty("SINK_HOST", "127.0.0.1");
    }

    protected void init() {
        ManagementCenterConfig mcc = new ManagementCenterConfig();
        mcc.setEnabled(true);
        mcc.setUrl("http://localhost:8080/hazelcast-mancenter");

        ccfg = new ClientConfig();
        ccfg.getGroupConfig().setName("dev").setPassword("ignored");
        ccfg.getNetworkConfig().addAddress(IMDG_HOST);

        jc = new JetConfig();
        Config hazelcastConfig = jc.getHazelcastConfig();
        // Avoid collision between the external IMDG (remoteMap) and the internal IMDG
        NetworkConfig networkConfig = hazelcastConfig.getNetworkConfig();
        //networkConfig.getJoin().getMulticastConfig().setEnabled(false);
        networkConfig.setPort(5710); // Group name defaults to Jet but port still defaults to 5701
        //hazelcastConfig.setManagementCenterConfig(mcc);
        jc.setHazelcastConfig(hazelcastConfig);

//        Config config = new Config();
//        config.getGroupConfig().setName("dev").setPassword("ignored");
//        config.getNetworkConfig().getJoin().getTcpIpConfig().addMember(IMDG_HOST);
        hazelcast = HazelcastClient.newHazelcastClient();
        distributedES = hazelcast.getExecutorService("execSvc");

    }

    private static class CreditLimitRuleTask implements Runnable, Serializable {
        public void run() {
            CreditLimitRule creditLimitRule = new CreditLimitRule();
            creditLimitRule.run(CreditLimitRule.RULE_NAME);
            // TODO: configure filter to take odd-numbered transactions
        }
    }

    private static class AdjustMerchantAvgTask implements Runnable, Serializable {
        public void run() {
            System.out.println("Runnable that should start Jet has been started");
            AdjustMerchantTransactionAverage amta = new AdjustMerchantTransactionAverage();
            amta.run();
        }
    }

    public static boolean isEven(String txnId) {
        int numericID = Integer.parseInt(txnId);
        boolean result =  (numericID % 2) == 0;
        return result;
    }

    public static void main(String[] args) {
        Launcher main = new Launcher();
        main.init();

        // This runs the Jet pipeline version of the rule.   Will fail if TxnGenMain not running.
        // Working OK, but disabling for now to focus on debugging the EntryProcessor
        //main.distributedES.submit(new CreditLimitRuleTask());


        // This runs the EntryProcessor version of the rule.   Not getting any hits.
        // Are we getting the map from the 'wrong' instance? (Internal vs. external?)
        IMap<String, Transaction> preAuthMap = main.hazelcast.getMap("preAuth");
        System.out.println("initial PreAuth size " + preAuthMap.size());  // should be non-zero
//        preAuthMap.addEntryListener(new TransactionMapListener(main.hazelcast),
//                entry -> (isEven(entry.getValue().getID())), true);

        preAuthMap.addEntryListener(new TransactionMapListener(main.hazelcast),
                true);

        // Start performance monitoring.  Just based on laptop performance 'feel', seems this
        // is fairly intrusive and probably should not be on by default.
        if (COLLECT_PERFORMANCE_STATS) {
            PerfMonitor.setRingBuffer(main.hazelcast.getRingbuffer("tpsResults"));
            PerfMonitor.startTimers();
        }

        // Start up the various Jet pipelines
        // TODO: not sure there's any advantage to using IMDG executor service here
        // over plain Java
        AdjustMerchantAvgTask merchantAvgTask = new AdjustMerchantAvgTask();
        //main.distributedES.submit(merchantAvgTask);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(merchantAvgTask);

        // This has no purpose other than monitoring the backlog during debug
        while (true) {
            try {
                Thread.sleep(30000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (preAuthMap.size() > 1)
                System.out.println("Transaction backlog (preAuth map size) " + preAuthMap.size());  // should be non-zero
        }
    }
}
