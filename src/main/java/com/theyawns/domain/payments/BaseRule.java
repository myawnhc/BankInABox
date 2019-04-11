package com.theyawns.domain.payments;

import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.config.Config;
import com.hazelcast.config.ManagementCenterConfig;
import com.hazelcast.jet.Jet;
import com.hazelcast.jet.JetInstance;
import com.hazelcast.jet.Job;
import com.hazelcast.jet.config.JetConfig;
import com.hazelcast.jet.pipeline.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.hazelcast.jet.Util.mapEventNewValue;
import static com.hazelcast.jet.Util.mapPutEvents;

public abstract class BaseRule {

    public static final String IMDG_HOST = "localhost:5701";
    protected ClientConfig ccfg;
    protected JetConfig jc;

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
        hazelcastConfig.getNetworkConfig().setPort(5710); // Group name defaults to Jet but port still defaults to 5701
        hazelcastConfig.setManagementCenterConfig(mcc);

        jc.setHazelcastConfig(hazelcastConfig);

    }

    protected StreamStage<TransactionWithRules> getEnrichedJournal(Pipeline p) {
        StreamStage<Transaction> txns = p.drawFrom(Sources.remoteMapJournal("preAuth", ccfg, mapPutEvents(),
                mapEventNewValue(), JournalInitialPosition.START_FROM_OLDEST) );

        StreamStage<TransactionWithRules> enriched =
                txns.mapUsingContext(getJetContext(), (JetInstance jet, Transaction t) -> {
                    List<Job> activeJobs = jet.getJobs();
                    Set<String> rules = new HashSet<>();
                    for (Job j : activeJobs) {
                        rules.add(j.getName());
                    }
                    return new TransactionWithRules(t, rules);
                });
        return enriched;
    }

    abstract Pipeline buildPipeline();

    private ContextFactory<JetInstance> getJetContext() {
        return ContextFactory.withCreateFn(jet -> { return Jet.newJetClient(); } );
    }


    public void run() {

        init();
        Pipeline p = buildPipeline();

        System.out.println("Starting Jet instance"); // TODO: should defer this until we're ready to run the job!
        JetInstance jet = Jet.newJetInstance(jc);

        try {
            jet.newJob(p).join();
        } finally {
            jet.shutdown();
        }
    }
}
