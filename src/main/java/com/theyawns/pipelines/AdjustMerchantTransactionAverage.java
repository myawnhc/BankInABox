package com.theyawns.pipelines;

import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.XmlClientConfigBuilder;
import com.hazelcast.config.Config;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.config.XmlConfigBuilder;
import com.hazelcast.core.IMap;
import com.hazelcast.jet.Jet;
import com.hazelcast.jet.JetInstance;
import com.hazelcast.jet.Job;
import com.hazelcast.jet.aggregate.AggregateOperations;
import com.hazelcast.jet.config.JetConfig;
import com.hazelcast.jet.datamodel.KeyedWindowResult;
import com.hazelcast.jet.pipeline.*;
import com.theyawns.Constants;
import com.theyawns.domain.payments.Merchant;
import com.theyawns.domain.payments.Transaction;

import java.io.Serializable;

import static com.hazelcast.jet.Util.mapEventNewValue;
import static com.hazelcast.jet.Util.mapPutEvents;

public class AdjustMerchantTransactionAverage implements Serializable {

    private JetConfig jetConfig;

    protected void init() {

        XmlConfigBuilder xccb = new XmlConfigBuilder(); // Reads hazelcast.xml
        Config hazelcastConfig = xccb.build();
        NetworkConfig networkConfig = hazelcastConfig.getNetworkConfig();
        networkConfig.setPort(5710); // Avoid collision between internal and external IMDG clusters
        hazelcastConfig.getCPSubsystemConfig().setCPMemberCount(0); // no CP needed on internal cluster
        hazelcastConfig.getGroupConfig().setName("jet-dev"); // try not to confuse mancenter

        jetConfig = new JetConfig();
        jetConfig.setHazelcastConfig(hazelcastConfig);
    }

    public void run() {
        init();
        Pipeline p = buildPipeline();
        JetInstance jet = Jet.newJetInstance(jetConfig);

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

        try {

            XmlClientConfigBuilder xccb = new XmlClientConfigBuilder(); // Reads hazelcast-client.xml
            ClientConfig clientConfig = xccb.build();

            Pipeline p = Pipeline.create();

            // Stage 1: Draw transactions from the mapJournal associated with the preAuth map
            StreamStage<Transaction> txns = p.drawFrom(Sources.<Transaction, String, Transaction>remoteMapJournal(Constants.MAP_PREAUTH, clientConfig, mapPutEvents(),
                    mapEventNewValue(), JournalInitialPosition.START_FROM_OLDEST))
                    .withIngestionTimestamps()
                    .setName("Draw Transactions from preAuth map");

            // Have a very large window to improve accuracy over time, but slide over shorter intervals to get initial updates flowing earlier
            WindowDefinition window = WindowDefinition.sliding(100000, 5000);

            // PEEK here shows valid looking merchant ids
            StreamStage<KeyedWindowResult<String, Double>> merchantAverages = txns.window(window)
                    .groupingKey(Transaction::getMerchantId)
                    .aggregate(
                            AggregateOperations.averagingDouble(
                                    Transaction::getAmount))
                    .setName("Aggregate average transaction amount by merchant");

            ContextFactory<IMap<String, Merchant>> contextFactory =
                    ContextFactory.withCreateFn(x -> {
                        return Jet.newJetClient(/*ccfg*/).getMap(Constants.MAP_MERCHANT);
                    });

            // arg0: ContextFactory<C> will give us a Merchant for KWR<MerchantID, Double>
            // arg1: BiFunctionEx<C,T,R>  given Merchant, KWR<M,D>, emit Merchant with updated amt
            StreamStage<Merchant> updatedMerchants = merchantAverages.mapUsingContext(contextFactory,
                    (map, kwr) -> {
                        Merchant m = map.get(kwr.getKey());
                        m.setAvgTxnAmount(kwr.getValue());
                        return m;
                    }).setName("Retrieve merchant record from IMDG and update average txn amt");

            updatedMerchants.drainTo(Sinks.remoteMapWithMerging("merchantMap",
                    clientConfig,
                    /* toKeyFn */ Merchant::getMerchantId,
                    /* toValueFn */ Merchant::getObject,
                    /* mergeFn */ (Merchant o, Merchant n) -> {
                        // Don't change when average varies by less than one dollar
                        if (Math.abs(o.getAvgTxnAmount() - n.getAvgTxnAmount()) > 1.00) {
                            System.out.printf("Merchant %s average transaction amount updated from %.3f to %.3f\n", o.getMerchantId(),
                                    o.getAvgTxnAmount(), n.getAvgTxnAmount());
                        }
                        return n;
                    })).setName("Merge updated Merchant record back to IMDG merchantMap");

            return p;
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }
}
