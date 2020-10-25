package com.theyawns.ruleengine.jetimpl;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.XmlClientConfigBuilder;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.jet.*;
import com.hazelcast.jet.aggregate.AggregateOperations;
import com.hazelcast.jet.config.JetConfig;
import com.hazelcast.jet.config.JobConfig;
import com.hazelcast.jet.datamodel.Tuple2;
import com.hazelcast.jet.datamodel.WindowResult;
import com.hazelcast.jet.pipeline.*;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.hazelcast.map.IMap;
import com.theyawns.controller.Constants;
import com.theyawns.controller.config.EnvironmentSetup;
import com.theyawns.ruleengine.HasID;
import com.theyawns.ruleengine.ItemCarrier;
import com.theyawns.ruleengine.RuleSetRoutingInfo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hazelcast.jet.datamodel.Tuple2.tuple2;

public class JetStage1Pipeline<T extends HasID> implements Runnable {

    private final static ILogger log = Logger.getLogger(JetStage1Pipeline.class);

    private JetConfig    jetConfig;
    private ClientConfig imdgClientConfig;
    private ClientConfig jetClientConfig;

    private JetInstance jet;
    private HazelcastInstance imdg;

    //RuleEngineRoutingController<T> router;
    static IMap<String, RuleSetRoutingInfo> router;

    // Initialize before job starts --
    List<RuleSetRoutingInfo<T>> routingInfo;

    boolean externalJetCluster = true; // vs. embedded

//    public void setRouter(RuleEngineRoutingController<T> router) {
//        this.router = router;
//    }

    protected void init() {
        //log.info("BEGIN AdjustMerchantTransactionAverage init()");
        this.imdgClientConfig = new XmlClientConfigBuilder().build();
        this.jetClientConfig = new XmlClientConfigBuilder().build();

        jetClientConfig.getNetworkConfig().getAddresses().clear();
        jetClientConfig.getNetworkConfig().addAddress("127.0.0.1:5710");
        // This is just temporary until we move to client-server Jet usage
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

//    interface RoutingService<T> {
//        List<Tuple2<ItemCarrier, String>> getTopicNames(ItemCarrier ic);
//    }

    class RoutingService<T extends HasID> implements Serializable {
        //IMap<String, RuleSetRoutingInfo<T>> router;
        RuleSetRoutingInfo[] routingInfo;

        public RoutingService(RuleSetRoutingInfo[] initial) {
            routingInfo = initial;
            System.out.println("Initialized RoutingService with " + initial.length + " routes");
        }
        //@Override
        public Traverser<Tuple2<ItemCarrier<T>, String>> getTopicNames(ItemCarrier<T> carrier) {

            List<Tuple2<ItemCarrier<T>, String>> results = new ArrayList<>();
            for (RuleSetRoutingInfo info : routingInfo) {
                if (info.isApplicableTo(carrier.getItem())) {
                    results.add(tuple2(carrier, info.getQueueName()));
                    System.out.println("Added " + info.getQueueName());
                }
            }
            return Traversers.traverseIterable(results);
        }
    }

//    private Traverser<Tuple2<ItemCarrier<T>, RuleSetRoutingInfo<T>>> getRoutingInfo(ItemCarrier<T> carrier) {
//        List<Tuple2<ItemCarrier<T>, RuleSetRoutingInfo<T>>> results = new ArrayList<>();
//        IMap<String, RuleSetRoutingInfo<T>> router = this.router; // local copy makes us serializable
//        for (RuleSetRoutingInfo<T> info : router.values()) {
//            if (info.isApplicableTo(carrier.getItem())) {
//                results.add(tuple2(carrier, info));
//            }
//        }
//        return Traversers.traverseIterable(results);
//    }

    // Removed <T> throughout when making this static
//    private static Traverser<Tuple2<ItemCarrier, RuleSetRoutingInfo>> getRoutingInfo(ItemCarrier carrier) {
//        List<Tuple2<ItemCarrier, RuleSetRoutingInfo>> results = new ArrayList<>();
//        //IMap<String, RuleSetRoutingInfo> router = this.router; // local copy makes us serializable
//        for (RuleSetRoutingInfo info : router.values()) {
//            if (info.isApplicableTo(carrier.getItem())) {
//                results.add(tuple2(carrier, info));
//            }
//        }
//        return Traversers.traverseIterable(results);
//    }

    private Pipeline buildCompactPipeline() {
        Pipeline p = Pipeline.create();

        // Stage 1: Read, wrap, and route
        // 1a: Draw transactions from the mapJournal associated with the preAuth map
        p.readFrom(getMapJournal())
                .withIngestionTimestamps()
                .map(Map.Entry::getValue)
                .setName("Draw transactions from PreAuth event journal")

                // Stage 1b: Wrap with ItemCarrier
                .map(ItemCarrier::new)
                .map(ItemCarrier::setTimeEnqueuedForExecutor)
                .setName("Wrap items with ItemCarrier")

                // Stage 1c: Add routing info for each ruleset
//                .flatMap(getRoutingInfo)
//                .setName("Get routing info for rulesets that apply to the item")




        // fake pipeline end for development and testing
                .rollingAggregate(AggregateOperations.counting())
                .writeTo(Sinks.logger());
        return p;
    }


    // work out the details here, then shorten to compact version above
    private Pipeline buildVerbosePipeline() {
        try {
            Pipeline p = Pipeline.create();

            // Stage 1: Read, wrap, and route
            // 1a: Draw transactions from the mapJournal associated with the preAuth map
            StreamStage<T> txns = p.readFrom(getMapJournal())
                    .withIngestionTimestamps()
                    .map(Map.Entry::getValue)
                    .setName("Draw transactions from PreAuth event journal");

            // Stage 1b: Wrap with ItemCarrier
            StreamStage<ItemCarrier<T>> carriers = txns.map(ItemCarrier::new)
                    .map(ItemCarrier::setTimeEnqueuedForExecutor)
                    .setName("Wrap items with ItemCarrier");

            // Stage 1c: Route to applicable RuleSets
            // Flat map carriers to (carrier, routing info) pairs
//            StreamStage<Tuple2<ItemCarrier<T>, RuleSetRoutingInfo<T>>> routes
//                    = carriers.flatMap(JetStage1Pipeline::getRoutingInfo)
//                    .setName("Get routing info for rulesets that apply to the item");

//            // Fails at runtime because router is null
//            StreamStage<Tuple2<ItemCarrier<T>, RuleSetRoutingInfo<T>>> routes =
//                    carriers.flatMap(carrier -> {
//
//                List<Tuple2<ItemCarrier<T>, RuleSetRoutingInfo<T>>> results = new ArrayList<>();
//                System.out.println("Router is " + router);
//                if (router != null) System.out.println(" values() size " + router.values().size());
//                for (RuleSetRoutingInfo info : router.values()) {
//                    if (info.isApplicableTo(carrier.getItem())) {
//                        results.add(tuple2(carrier, info));
//                    }
//                }
//                return Traversers.traverseIterable(results);
//            }).setName("Get routing info for rulesets that apply to the item");



//            JetInstance jet = this.jet;
//            System.out.println("Jet is " + jet);
//            IMap<String, RuleSetRoutingInfo<T>> routingInfo = imdg.getMap(Constants.MAP_RS_ROUTING);
//                    // jet.getMap(Constants.MAP_RS_ROUTING);  empty; no jet.getRemoteMap call
//            System.out.println("Map is " + routingInfo);
//            System.out.println("Map values are " + routingInfo.values()); // EMPTY
//
//            // still getting flatMapFn not serializable
//            StreamStage<Tuple2<ItemCarrier<T>, RuleSetRoutingInfo<T>>> routes =
//                    carriers.flatMap(carrier -> {
//                        List<Tuple2<ItemCarrier<T>, RuleSetRoutingInfo<T>>> results = new ArrayList<>();
//                        // NOPE - ClientMapProxy is not serializable
//                        for (RuleSetRoutingInfo<T> info : routingInfo.values()) {
//                            if (info.isApplicableTo(carrier.getItem())) {
//                                results.add(tuple2(carrier, info));
//                            }
//                        }
//                        return Traversers.traverseIterable(results);
//                    }).setName("Get routing info for rulesets that apply to the item");

            //carriers.mapUsingIMap(Constants.MAP_RS_ROUTING, keyfn, mapfn);

//            ServiceFactory<?, RoutingService<T>> routingService =
//                    ServiceFactories.sharedService(ctx -> {
//                        JetInstance jet = ctx.jetInstance();
//                        // we really want a remote IMDG map here but will worry about that later
//                        IMap<String,RuleSetRoutingInfo<T>> router = jet.getMap(Constants.MAP_RS_ROUTING);
//                        RuleSetRoutingInfo[] info = router.values().toArray(new RuleSetRoutingInfo[router.size()]);
//                        return new RoutingService<T>(info);
//                    })
//                    .toNonCooperative();

//            StreamStage<Tuple2<ItemCarrier<T>, String>> topics = carriers.flatMapUsingService(routingService,
//                    (service, carrier) -> service.getTopicNames(carrier));

            // Write to a Reliable Topic that all RuleSet Jobs will read - filtering
            // will be done by those jobs rather than by a router task here

            // Write carrier item into the ReliableTopic for each qualified ruleset
            // TODO: decide on a good name for this and add it to constants
            carriers.writeTo(Sinks.reliableTopic("Items"));



            // Extract topic name from RoutingInfo
//            StreamStage<Tuple2<String, ItemCarrier<T>>> topics = routes.map(
//                    t -> tuple2(t.f1().getQueueName(), t.f0()));

            // We will get the names from RuleSetRoutingInfo, for this stage of
            // development where the list is fixed, we'll save the effort.
//            addPipelineStagesFor(topics, Constants.QUEUE_LOCATION);
//            addPipelineStagesFor(topics, Constants.QUEUE_MERCHANT);

            // Note that we will eventually fork the pipeline here - one branch will write the
            // Carriers to appropriate output sinks, the other will window and
            // count to give us a TPS and/or latency for the Stage 1 process.

//            // get one 'branch' working here then make it work for multiple
//            // We'll use location as the test
//            StreamStage<Tuple2<String, ItemCarrier<T>>> locationTuples = topics.filter( t -> t.f0().equals(Constants.QUEUE_LOCATION))
//                    .setName("Filter items matching " + Constants.QUEUE_LOCATION);
//
//            // Now that we're filtered we don't need the queue any longer
//            StreamStage<ItemCarrier<T>> locationItems = locationTuples.map( t -> t.f1())
//                    .setName("Extract ItemCarrier from Tuple");
//
//            // Write carrier item into the ReliableTopic for each qualified ruleset
//            locationItems.writeTo(Sinks.reliableTopic(Constants.QUEUE_LOCATION));
//
//            // Output count every 30 seconds
            SlidingWindowDefinition smallWindow = WindowDefinition.tumbling(TimeUnit.SECONDS.toMillis(30));
            // TODO: add parameterized type info when pipeline is complete
            StageWithWindow sww = carriers.window(smallWindow);

            StreamStage<WindowResult<Long>> sw2 = sww.aggregate(AggregateOperations.counting());
            sw2.writeTo(Sinks.logger(count -> count.result() / 30 + " TPS")); // Should see count of items


            return p;
        } catch (Throwable t) {
            t.printStackTrace();
            return null;
        }
    }

    private void addPipelineStagesFor(StreamStage<Tuple2<ItemCarrier<T>, String>> topics, String topicName) {
        // get one 'branch' working here then make it work for multiple
        // We'll use location as the test
        StreamStage<Tuple2<ItemCarrier<T>, String>> locationTuples = topics.filter( t -> t.f1().equals(topicName))
                .setName("Filter items matching " + topicName);

        // Now that we're filtered we don't need the queue any longer
        StreamStage<ItemCarrier<T>> locationItems = locationTuples.map( t -> t.f0())
                .setName("Extract ItemCarrier from Tuple");

        // Write carrier item into the ReliableTopic for each qualified ruleset
        locationItems.writeTo(Sinks.reliableTopic(topicName));

        // Output count every 30 seconds
        SlidingWindowDefinition smallWindow = WindowDefinition.tumbling(TimeUnit.SECONDS.toMillis(30));
        // TODO: add parameterized type info when pipeline is complete
        StageWithWindow sww = locationItems.window(smallWindow);

        StreamStage<WindowResult<Long>> sw2 = sww.aggregate(AggregateOperations.counting());
        sw2.writeTo(Sinks.logger(count -> count.result() / 30 + " TPS writing to " + topicName));


    }

    public void run() {
        this.init();

        if (externalJetCluster) {
            log.info("Setting JetClientConfig to point to JetInABox and creating new client");
            jetClientConfig.setClusterName("JetInABox");
            jet = Jet.newJetClient(this.jetClientConfig);
            log.info("Connected to Jet cluster in client/server mode" + jet.getName());
        } else {
            jet = Jet.newJetInstance(jetConfig);
            log.info("Connected to Jet cluster in embedded mode " + jet.getName());
        }


        imdg = HazelcastClient.newHazelcastClient(imdgClientConfig);
        System.out.println("IMDG cluster is " + imdgClientConfig.getClusterName());
        router = imdg.getMap(Constants.MAP_RS_ROUTING);
        System.out.printf("Router has %d entries\n", router.values().size());

        try {
            Pipeline pipeline = buildVerbosePipeline();

            if (pipeline == null) {
                log.severe("***** Pipeline construction failed, Jet job will exit");
                return;
            }

            JobConfig jobConfig = new JobConfig();
            jobConfig.setName("Ingest and Queue");
            Job job = jet.newJob(pipeline, jobConfig);
            log.info("Launched " + job.getName() + ", status==" + job.getStatus());
            job.join();
        } catch (Exception e) {
            log.severe(this.getClass().getName() + " EXCEPTION " + e.getMessage());
            e.printStackTrace(System.out);
        }
    }
}
