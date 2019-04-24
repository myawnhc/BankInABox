package com.theyawns.pipelines;

import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.config.Config;
import com.hazelcast.config.ManagementCenterConfig;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.jet.Jet;
import com.hazelcast.jet.JetInstance;
import com.hazelcast.jet.Job;
import com.hazelcast.jet.aggregate.AggregateOperations;
import com.hazelcast.jet.config.JetConfig;
import com.hazelcast.jet.datamodel.KeyedWindowResult;
import com.hazelcast.jet.pipeline.*;
import com.theyawns.domain.payments.Transaction;

import java.io.Serializable;

import static com.hazelcast.jet.Util.mapEventNewValue;
import static com.hazelcast.jet.Util.mapPutEvents;

public class AdjustMerchantTransactionAverage implements Serializable {

    private ClientConfig ccfg;
    private String IMDG_HOST = "localhost"; // TODO: properties files
    private JetConfig jc;

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
        System.out.println("AdjustMerchantTransactionAverage.init() sets JetConfig to use port 5710");

    }

    public void run() {
        System.out.println(">>>init");
        init();
        System.out.println("<<<<init");
        Pipeline p = buildPipeline();

        System.out.println("Starting Jet instance"); // TODO: should we connect to a running instance?
        JetInstance jet = Jet.newJetInstance(jc);

        try {
            Job job = jet.newJob(p);
            job.getConfig().setName("AdjustMerchantTxnAverage");
            System.out.println("Running " + job.getConfig().getName());
            job.join();
        } finally {
            jet.shutdown();
        }
    }

    private Pipeline buildPipeline() {

        Pipeline p = Pipeline.create();

        // Stage 1: Draw transactions from the mapJournal associated with the preAuth map
        StreamStage<Transaction> txns = p.drawFrom(Sources.<Transaction, String, Transaction>remoteMapJournal("preAuth", ccfg, mapPutEvents(),
                mapEventNewValue(), JournalInitialPosition.START_FROM_OLDEST) )
                .withoutTimestamps()
                .setName("Draw from preAuth map");

        // Have a very large window to improve accuracy over time, but slide over short intervals to get initial updates flowing earlier
        WindowDefinition window = WindowDefinition.sliding(100000, 1000);

        StreamStage<KeyedWindowResult<String, Double>> merchantAverages = txns.window(window)
                .groupingKey(Transaction::getMerchantId)
                .aggregate(AggregateOperations.averagingDouble(Transaction::getAmount))
                .setName("Aggregate average transaction amount by merchant");

        // TODO: merge values back into merchant map

        merchantAverages.drainTo(Sinks.logger());

        return p;

    }




}
