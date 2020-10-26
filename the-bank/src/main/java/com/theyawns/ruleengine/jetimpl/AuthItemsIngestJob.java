package com.theyawns.ruleengine.jetimpl;

import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.XmlClientConfigBuilder;
import com.hazelcast.jet.*;
import com.hazelcast.jet.aggregate.AggregateOperations;
import com.hazelcast.jet.config.JetConfig;
import com.hazelcast.jet.config.JobConfig;
import com.hazelcast.jet.datamodel.WindowResult;
import com.hazelcast.jet.pipeline.*;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.theyawns.controller.Constants;
import com.theyawns.controller.config.EnvironmentSetup;
import com.theyawns.ruleengine.HasID;
import com.theyawns.ruleengine.ItemCarrier;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class AuthItemsIngestJob<T extends HasID> implements Runnable {

    private final static ILogger log = Logger.getLogger(AuthItemsIngestJob.class);

    private JetConfig    jetConfig;
    private ClientConfig imdgClientConfig;
    private ClientConfig jetClientConfig;

//    private JetInstance jet;
//    private HazelcastInstance imdg;
//
//    //RuleEngineRoutingController<T> router;
//    static IMap<String, RuleSetRoutingInfo> router;
//
//    // Initialize before job starts --
//    List<RuleSetRoutingInfo<T>> routingInfo;

    boolean externalJetCluster = true; // vs. embedded


    protected void init() {
        this.imdgClientConfig = new XmlClientConfigBuilder().build();
        this.jetClientConfig = new XmlClientConfigBuilder().build();

        jetClientConfig.getNetworkConfig().getAddresses().clear();
        jetClientConfig.getNetworkConfig().addAddress("127.0.0.1:5710");
        this.jetConfig = new JetConfig();

        // IMDG (used for RemoteMap access)
        if (this.imdgClientConfig.getNetworkConfig().getKubernetesConfig().isEnabled()
                && this.imdgClientConfig.getNetworkConfig().getAddresses().size() > 0) {
            log.info("IMDG Remove listed server addresses in favour of Kubernetes discovery.");
            this.imdgClientConfig.getNetworkConfig().setAddresses(new ArrayList<>());
            this.imdgClientConfig.setClusterName("BankInABox");

            this.imdgClientConfig.getNetworkConfig()
                    .getKubernetesConfig().setProperty("service-dns", EnvironmentSetup.IMDG_SERVICE);
            this.imdgClientConfig.getNetworkConfig()
                    .getKubernetesConfig().setProperty("service-port", EnvironmentSetup.IMDG_PORT);

            log.info("IMDG Kubernetes config " + this.imdgClientConfig.getNetworkConfig().getKubernetesConfig());
        }

        // Jet
        if (this.jetClientConfig.getNetworkConfig().getKubernetesConfig().isEnabled()
                && this.jetClientConfig.getNetworkConfig().getAddresses().size() > 0) {
            log.info("Jet Remove listed server addresses in favour of Kubernetes discovery.");
            this.jetClientConfig.getNetworkConfig().setAddresses(new ArrayList<>());
            this.jetClientConfig.setClusterName("JetInABox");

            this.jetClientConfig.getNetworkConfig()
                    .getKubernetesConfig().setProperty("service-dns", EnvironmentSetup.JET_SERVICE);
            this.jetClientConfig.getNetworkConfig()
                    .getKubernetesConfig().setProperty("service-port", EnvironmentSetup.JET_PORT);

            log.info("Jet Kubernetes config " + this.imdgClientConfig.getNetworkConfig().getKubernetesConfig());
        } else if (externalJetCluster) {
            this.jetClientConfig.setClusterName("JetInABox");
        }
    }

    // Helper functions - keeps the pipeline more compact and readable
    private StreamSource<Map.Entry<String, T>> getMapJournal() {
        StreamSource<Map.Entry<String, T>> rjSource =
                Sources.remoteMapJournal(Constants.MAP_PREAUTH,
                        imdgClientConfig,
                        JournalInitialPosition.START_FROM_OLDEST);
        return rjSource;
    }

    private Pipeline buildCompactPipeline() {
        Pipeline p = Pipeline.create();

        // Stage 1: Read, wrap, and route
        // 1a: Draw transactions from the mapJournal associated with the preAuth map
        StreamStage<ItemCarrier<T>> carriers = p.readFrom(getMapJournal())
                .withIngestionTimestamps()
                .map(Map.Entry::getValue)
                .setName("Draw transactions from PreAuth event journal")

                // Stage 1b: Wrap with ItemCarrier
                .map(ItemCarrier::new)
                .map(ItemCarrier::setTimeEnqueuedForExecutor)
                .setName("Wrap items with ItemCarrier");

        // Stage 1c: Write to a Reliable Topic
        carriers.writeTo(Sinks.reliableTopic("Items"))
                .setName("Write to Reliable Topic");


        // These steps are not needed for the pipeline to function, just
        // using to track throughput.
        // Output count every 30 seconds
        SlidingWindowDefinition smallWindow = WindowDefinition.tumbling(TimeUnit.SECONDS.toMillis(30));
        // Add a tumbling window over the stream, 30 second slices
        StageWithWindow timingWindow = carriers.window(smallWindow);
        // Aggregate and output the count of items in each 30 second slide
        StreamStage<WindowResult<Long>> itemCount = timingWindow.aggregate(AggregateOperations.counting());
        itemCount.writeTo(Sinks.logger(count -> count.result() / 30 + " TPS")); // Should see count of items

        return p;
    }

    /* Functionally identical to the compact pipeline; makes the types at each stage
     * explicit for instructional purposes
     */
    private Pipeline buildVerbosePipeline() {
        try {
            Pipeline p = Pipeline.create();

            // Stage 1: Read, wrap, and route
            // 1a: Draw transactions from the mapJournal associated with the preAuth map
            StreamStage<T> txns = p.readFrom(getMapJournal())
                    .withIngestionTimestamps()
                    .map(Map.Entry::getValue)
                    .setName("Draw transactions from PreAuth event journal");

            // Stage 1b: Wrap with ItemCarrier, set time enqueued
            StreamStage<ItemCarrier<T>> carriers = txns.map(ItemCarrier::new)
                    .map(ItemCarrier::setTimeEnqueuedForExecutor)
                    .setName("Wrap items with ItemCarrier");

            // Write to a Reliable Topic that all RuleSet Jobs will read - filtering
            // will be done by those jobs rather than by a router task here

            // Write carrier item into the ReliableTopic for each qualified ruleset
            // TODO: decide on a good name for this and add it to constants
            SinkStage itemSink = carriers.writeTo(Sinks.reliableTopic(Constants.TOPIC_AUTH_ITEMS))
                    .setName("Write to Reliable Topic");

            // Output count every 30 seconds
            SlidingWindowDefinition smallWindow = WindowDefinition.tumbling(TimeUnit.SECONDS.toMillis(30));
            StageWithWindow timingWindow = carriers.window(smallWindow);

            StreamStage<WindowResult<Long>> itemCount = timingWindow.aggregate(AggregateOperations.counting());
            itemCount.writeTo(Sinks.logger(count -> count.result() / 30 + " TPS")); // Should see count of items


            return p;
        } catch (Throwable t) {
            t.printStackTrace();
            return null;
        }
    }

    public void run() {
        this.init();
        JetInstance jet;

        if (externalJetCluster) {
            log.info("Setting JetClientConfig to point to JetInABox and creating new client");
            jetClientConfig.setClusterName("JetInABox");
            jet = Jet.newJetClient(this.jetClientConfig);
            log.info("Connected to Jet cluster in client/server mode" + jet.getName());
        } else {
            jet = Jet.newJetInstance(jetConfig);
            log.info("Connected to Jet cluster in embedded mode " + jet.getName());
        }

        try {
            Pipeline pipeline = buildVerbosePipeline();

            if (pipeline == null) {
                log.severe("***** Pipeline construction failed, Jet job will exit");
                return;
            }

            JobConfig jobConfig = new JobConfig();
            jobConfig.setName("Ingest, Wrap, and Publish");
            Job job = jet.newJob(pipeline, jobConfig);
            log.info("Launched " + job.getName() + ", status==" + job.getStatus());
            job.join();
        } catch (Exception e) {
            log.severe(this.getClass().getName() + " EXCEPTION " + e.getMessage());
            e.printStackTrace(System.out);
        }
    }
}
