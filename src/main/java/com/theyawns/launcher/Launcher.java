package com.theyawns.launcher;

import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.config.Config;
import com.hazelcast.config.ManagementCenterConfig;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.jet.config.JetConfig;
import com.theyawns.domain.payments.CreditLimitRule;
import com.theyawns.domain.payments.Transaction;
import com.theyawns.listeners.TransactionMapListener;

import java.io.Serializable;
import java.util.concurrent.ExecutorService;

public class Launcher {

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

        Config config = new Config();
        config.getGroupConfig().setName("dev").setPassword("ignored");
        config.getNetworkConfig().getJoin().getTcpIpConfig().addMember(IMDG_HOST);
        hazelcast = Hazelcast.newHazelcastInstance(config);
        distributedES = hazelcast.getExecutorService("execSvc");

    }

    private static class CreditLimitRuleTask implements Runnable, Serializable {
        public void run() {
            CreditLimitRule creditLimitRule = new CreditLimitRule();
            creditLimitRule.run(CreditLimitRule.RULE_NAME);
            // TODO: configure filter to take odd-numbered transactions
        }
    }

    public static boolean isEven(String txnId) {
        int numericID = Integer.parseInt(txnId);
        boolean result =  (numericID % 2) == 0;
        System.out.println("isEvent " + result);
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
        System.out.println("PreAuth size " + preAuthMap.size());  // should be non-zero
        preAuthMap.addEntryListener(new TransactionMapListener(main.hazelcast),
                entry -> (isEven(entry.getValue().getID())), true);

        while (true) {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("PreAuth size " + preAuthMap.size());  // should be non-zero
        }

        // TODO: start performance monitor

        // TODO: start transaction generator
    }
}
