package com.theyawns.launcher;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.jet.function.PredicateEx;
import com.theyawns.Constants;
import com.theyawns.domain.payments.CreditLimitRule;
import com.theyawns.domain.payments.ResultMapMonitor;
import com.theyawns.domain.payments.Transaction;
import com.theyawns.listeners.TransactionMapListener;
import com.theyawns.perfmon.PerfMonitor;

import java.io.Serializable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** This is not the mainstream launcher.
 *
 * This is to run a version of the credit limit run in IMDG and in Jet, feeding each of them half of the
 * transactions, in order to see relative latency and TPS information.  This is not rigorous enough to serve as
 * any sort of a valid benchmark but nevertheless might provide interesting insight.
 */
public class DualLauncher {

    public static final Boolean COLLECT_PERFORMANCE_STATS = true;

//    public static final String IMDG_HOST = "localhost:5701";
//    protected ClientConfig ccfg;
//    protected JetConfig jc;

//    //protected static final int SINK_PORT = 2004;
//    protected static String SINK_HOST;

    protected HazelcastInstance hazelcast;
//    protected ExecutorService distributedES;

//    static {
//        System.setProperty("hazelcast.multicast.group", "228.19.18.20");
//        SINK_HOST = System.getProperty("SINK_HOST", "127.0.0.1");
//    }

    protected void init() {
//        ManagementCenterConfig mcc = new ManagementCenterConfig();
//        mcc.setEnabled(true);
//        mcc.setUrl("http://localhost:8080/hazelcast-mancenter");

//        ccfg = new ClientConfig();
//        ccfg.getGroupConfig().setName("dev").setPassword("ignored");
//        ccfg.getNetworkConfig().addAddress(IMDG_HOST);
//        ccfg.getSerializationConfig().addDataSerializableFactory(101, new IDSFactory());

//        jc = new JetConfig();
//        Config hazelcastConfig = jc.getHazelcastConfig();
//        // Avoid collision between the external IMDG (remoteMap) and the internal IMDG
//        NetworkConfig networkConfig = hazelcastConfig.getNetworkConfig();
//        //networkConfig.getJoin().getMulticastConfig().setEnabled(false);
//        networkConfig.setPort(5710); // Group name defaults to Jet but port still defaults to 5701
//        //hazelcastConfig.setManagementCenterConfig(mcc);
//
//        EventJournalConfig ejc = new EventJournalConfig()
//                .setMapName(Constants.MAP_PREAUTH)
//                .setEnabled(true)
//                .setCapacity(1000000);
//        hazelcastConfig.addEventJournalConfig(ejc);
//        hazelcastConfig.getSerializationConfig().addDataSerializableFactory(101, new IDSFactory());
//
//        jc.setHazelcastConfig(hazelcastConfig);

        hazelcast = HazelcastClient.newHazelcastClient();
//        distributedES = hazelcast.getExecutorService("execSvc");

    }

    // The Jet implementation of the Credit Limit rule
    private static class CreditLimitRuleTask implements Runnable, Serializable {
        public void run() {
            PredicateEx<Transaction> filter = (PredicateEx<Transaction>) transaction -> isOdd(transaction.getID());

            CreditLimitRule creditLimitRule = new CreditLimitRule();
            creditLimitRule.setFilter(filter);
            creditLimitRule.run(CreditLimitRule.RULE_NAME);
        }
    }

    public static boolean isEven(String txnId) {
        int numericID = Integer.parseInt(txnId);
        boolean result =  (numericID % 2) == 0;
        return result;
    }

    public static boolean isOdd(String txnId) {
        return !isEven(txnId);
    }

    public static void main(String[] args) {
        DualLauncher main = new DualLauncher();
        main.init();

        ResultMapMonitor tmon = new ResultMapMonitor(main.hazelcast);
        new Thread(tmon).start();

        // This runs the Jet pipeline version of the rule.   Will fail if TxnGenMain not running.
        // Working OK, but disabling for now to focus on debugging the EntryProcessor
        ExecutorService clrexec = Executors.newSingleThreadExecutor();
        clrexec.submit(new CreditLimitRuleTask());


        // This runs the EntryProcessor version of the rule.   Not getting any hits.
        // Are we getting the map from the 'wrong' instance? (Internal vs. external?)
        IMap<String, Transaction> preAuthMap = main.hazelcast.getMap(Constants.MAP_PREAUTH);
        System.out.println("initial PreAuth size " + preAuthMap.size());  // should be non-zero

        // Run entry listener only on even number transactions
        preAuthMap.addEntryListener(new TransactionMapListener(main.hazelcast),
                entry -> (isEven(entry.getValue().getID())), true);

        // Start performance monitoring.  Just based on laptop performance 'feel', seems this
        // is fairly intrusive and probably should not be on by default.
        if (COLLECT_PERFORMANCE_STATS) {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.submit(PerfMonitor.getInstance());
//            PerfMonitor.setRingBuffers(main.hazelcast.getRingbuffer("JetTPSResults"),
//                                       main.hazelcast.getRingbuffer("IMDGTPSResults"));
//            PerfMonitor.startTimers();
        }

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
