package com.theyawns.domain.payments;

import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.config.Config;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.jet.Jet;
import com.hazelcast.jet.JetInstance;
import com.hazelcast.jet.Job;
import com.hazelcast.jet.config.JetConfig;
import com.hazelcast.jet.pipeline.*;

import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.hazelcast.jet.Util.mapEventNewValue;
import static com.hazelcast.jet.Util.mapPutEvents;

public abstract class BaseRule implements Serializable {

    public static final String IMDG_HOST = "10.216.1.141:5701";
    protected ClientConfig ccfg;
    protected JetConfig jc;

    static {
        System.setProperty("hazelcast.multicast.group", "228.19.18.20");
    }

    protected void init() {
        //ManagementCenterConfig mcc = new ManagementCenterConfig();
        //mcc.setEnabled(true);
        //mcc.setUrl("http://localhost:8080/hazelcast-mancenter");

        ccfg = new ClientConfig();
        ccfg.getGroupConfig().setName("dev").setPassword("ignored");
        //ccfg.getNetworkConfig().addAddress(IMDG_HOST);

        jc = new JetConfig();
        Config hazelcastConfig = jc.getHazelcastConfig();
        // Avoid collision between the external IMDG (remoteMap) and the internal IMDG
        NetworkConfig networkConfig = hazelcastConfig.getNetworkConfig();
        //networkConfig.getJoin().getMulticastConfig().setEnabled(false);
        networkConfig.setPort(6701); // Group name defaults to Jet but port still defaults to 5701
        //hazelcastConfig.setManagementCenterConfig(mcc);
        jc.setHazelcastConfig(hazelcastConfig);

    }

    protected StreamStage<TransactionWithRules> getEnrichedJournal(Pipeline p) {
        StreamSourceStage<Transaction> txns = p.drawFrom(Sources.remoteMapJournal("preAuth", ccfg, mapPutEvents(),
                mapEventNewValue(), JournalInitialPosition.START_FROM_OLDEST) );

        StreamStage<TransactionWithRules> enriched =
                txns.withoutTimestamps()
                        .setName("draw from IMDG authMap")
                        .mapUsingContext(getJetContext(), (JetInstance jet, Transaction t) -> {
                    List<Job> activeJobs = jet.getJobs();
                    Set<String> rules = new HashSet<>();
                    for (Job j : activeJobs) {
                        rules.add(j.getName());
                    }
                    System.out.println("Adding " + activeJobs.size() + " rule ids to transaction " + t.getID() + "( acct " + t.getAccountNumber() + ")");
                    return new TransactionWithRules(t, rules);
                }).setName("Enrich with currently active rule info");
        return enriched;
    }

    abstract Pipeline buildPipeline();

    private ContextFactory<JetInstance> getJetContext() {
        return ContextFactory.withCreateFn(jet -> { return jet; } );
    }


    public void run() {

        init();
        Pipeline p = buildPipeline();

        System.out.println("Starting Jet instance");
        JetInstance jet = Jet.newJetInstance(jc);

        try {
            jet.newJob(p).join();
        } finally {
            jet.shutdown();
        }
    }
}
