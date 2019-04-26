package com.theyawns.ruleengine;

import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.core.IMap;
import com.hazelcast.jet.Jet;
import com.hazelcast.jet.JetInstance;
import com.hazelcast.jet.Job;
import com.hazelcast.jet.config.JobConfig;
import com.hazelcast.jet.pipeline.*;
import com.theyawns.aggregators.ResultAggregator;
import com.theyawns.domain.payments.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import static com.hazelcast.jet.Traversers.traverseIterator;
import static com.hazelcast.jet.Util.mapEventNewValue;
import static com.hazelcast.jet.Util.mapPutEvents;

@Deprecated
public class JetRuleEngine<T extends HasID> extends BaseRuleEngine<T> {

    private JetInstance jet;
    private RuleSetFactory<T> factory;
    //private HazelcastInstance embeddedHZ;
    private ClientConfig hzClientConfig;

    public JetRuleEngine(JetInstance jet, ClientConfig clientConfig, RuleSetFactory<T> factory) {
        super(jet.getHazelcastInstance());
        this.jet = jet;
        //this.embeddedHZ = hz;
        this.hzClientConfig = clientConfig;
        this.factory = factory;
        //resultMap = getHazelcast().getMap("resultMap");
    }

    public void run() {
        // Create Pipeline
        Pipeline p = createPipeline();
        // Create & Run Job
        createAndRunJob(p);

        System.out.println("Job finished, map contains " + getHazelcast().getMap("resultMap").size() + " entries");
        jet.shutdown();
    }

    private Pipeline createPipeline() {

        Pipeline p = Pipeline.create();

        // TODO: investigate using Kafka rather than an IQueue
        // Important to use the non-blocking poll() call here, not the blocking take() --
        // Jet has the retry / exponential backoff strategy to handle non-ready source
        // Switched to MapJournal, so this is not currently being used.
        StreamSource<T> iqueueSource = SourceBuilder
                .stream("iqueue-source", ctx -> ctx.jetInstance().getHazelcastInstance().getQueue("transactionQueue"))
                .<T>fillBufferFn((ctx, buf) -> {
                    for (int i = 0; i < 100; i++) {
                        T t = (T) ctx.poll();
                        if (t != null)
                            buf.add(t);
                        else
                            break;
                    }
                })
                .build();

        //HazelcastInstance hazelcast = getHazelcast();
        RuleSetFactory<T> localFactory = factory; // avoid capturing 'this'

        // Original design: draw from queue, flatMap items to all applicable rulesets.  However,
        // we eventually need to group and aggregate the ruleset results together, and each ruleset
        // can have different enrichment requirements ... so it seems more efficient to just split
        // the stream by ruleset right up front.
//        ClientConfig ccfg = new ClientConfig(); // TODO   Will this just default to localhost:5701 ?
//        ccfg.getNetworkConfig().addAddress(JetMain.IMDG_HOST);
//        ccfg.getGroupConfig().setName("dev").setPassword("ignored");

        ClientConfig ccfg = hzClientConfig; // need local copy to avoid capturing 'this'

        // This 'convenience method' gives ADDED and UPDATED events; note that if there are any UPDATES we'll
        // double-count and will need to change to the more verbose variant.  Design doesn't anticipate updates
        // So going with this for now,.
        StreamSourceStage<T> items =
                p.drawFrom(Sources.remoteMapJournal("pendingTransactions", ccfg, mapPutEvents(), mapEventNewValue(), JournalInitialPosition.START_FROM_OLDEST) );
                //.setName("Draw entries from pendingTransaction's MapJournal");

        //StreamStage<T> items = mapEntries.map((mapentry) -> mapentry.getValue()).setName("Extract values from journaled Map.Entries");

        // For each item in the input stream, emit applicable rulesets (1:Many)
        StreamStage<RuleSet<T>> ruleSetsForItem = items.withoutTimestamps().flatMap((T item) -> {
            Set<RuleSet<T>> setOfRuleSets = localFactory.getRuleSetsFor(item);
            return traverseIterator(setOfRuleSets.iterator());
        }).setName("Map stream items to applicable RuleSets");

        // PIPELINE BRANCHES - FRAUD BRANCH FOLLOWS

        StreamStage<RuleTask<T>> fraudTasks = ruleSetsForItem
                .filter((rset) -> rset.name.equals(FraudRuleSet.RULESET_ID))
                .setName("Apply FraudRuleSet")
                .flatMap((ruleset) -> {
                    List<RuleTask<T>> tasks = new ArrayList<>();
                    for (Rule rule : ruleset.rules) {
                        RuleTask<T> task = rule.createTask();
                        // new RuleTask<T>(rule, ruleset.name, ruleset.getItem());
                        tasks.add(task);
                    }
                    return traverseIterator(tasks.iterator());
                }).setName("Map Fraud RuleSet to Rule Execution Tasks ");

        fraudTasks.drainTo(Sinks.logger()).setName("Fraud branch of pipeline incomplete");

        // TEMPORARILY DURING DEVELOPMENT - NO FURTHER TASKS ON FRAUD BRANCH

        // PIPELINE BRANCH - PAYMENT BRANCH FOLLOWS

        // - In this branch, the Stream item type is Transaction, and the result type for RER and RSER is Boolean

        StreamStage<RuleTask<T>> paymentTasks = ruleSetsForItem
                .filter((rset) -> rset.name.equals(PaymentsRuleSet.RULESET_ID))
                .setName("Apply PaymentsRuleSet")
                .flatMap((ruleset) -> {
                    List<RuleTask<T>> tasks = new ArrayList<>();
                    for (Rule<T, ?> rule : ruleset.rules) {
                        RuleTask<T> task = rule.createTask();
                        // new RuleTask<T>(rule, ruleset.name, ruleset.getItem());
                        tasks.add(task);
                    }
                    return traverseIterator(tasks.iterator());
                }).setName("Map Payments RuleSet to Rule Execution Tasks ");

        // If enrichment was needed for evaluation of preconditions, would do that here

        // Filter on preconditions
        StreamStage<RuleTask<T>> filteredPaymentTasks = paymentTasks.filter(RuleTask::checkPreconditions)
                .setName("Filter rule tasks whose preconditions are not satisfied");


        // TODO: this isn't the right way to enrich with an external IMDG source.   See 4.8.2 in Jet Manual
        // Enrich for rule evaluation - for Payments, this is account info
        //IMap<String, Account> accountMap = embeddedHZ.getMap("accountMap"); // TODO: may get enrichment map name from task callback
        //IMap resultMap = embeddedHZ.getMap("resultMap");



        ContextFactory<IMap<String, Account>> contextFactory =
            ContextFactory.withCreateFn(x -> {
                // Doing this here to avoid capturing wider scope
                ClientConfig clientConfig = new ClientConfig();
                clientConfig.getNetworkConfig().addAddress(JetMain.IMDG_HOST);
                clientConfig.getGroupConfig().setName("dev").setPassword("ignored");
                //clientConfig.getNearCacheConfigMap().put("accountMap", new NearCacheConfig());
                return Jet.newJetClient(clientConfig).getMap("accountMap");
        });

        // mapUsingContext(ContextFactory<C>, DistributedBiFunction<? super C, ? super T, ? extends R> mapFn
        // This works if the RuleTask can have domain-specific methods and state
        StreamStage<RuleTask<T>> enrichedTasks = filteredPaymentTasks.mapUsingContext(contextFactory, (map, task) -> {
                    // this code should not be inlined here; task should have method that gives us function
                    if (task instanceof RuleTaskWithAccountInfo) {
                        ((RuleTaskWithAccountInfo)task).setAccountInfo(map, task);
                    }
                    return task;
                })
                .setName("Enrich rule tasks with Account info");

        // This will be preferred if RuleTask has no domain content
//        StreamStage<Tuple2<RuleTask<T>, Account>> enrichedTasks2 = filteredTasks.mapUsingIMap(accountMap, (map, task) -> {
//            Account account = map.get(task.item.getRuleId()); // needs to be account ID, not transaction ID!
//            return tuple2(task, account);
//        });


        //StreamStage<Tuple2<RuleTask<T>, Account>> ee = filteredTasks.hashJoin( ... )

        // after filter, this is next ... getRER will call the process() method of the rule
//        StreamStage<RuleEvaluationResult<T>> taskResults = enrichedTasks.mapUsingContext(getJetContext(),
//                (JetInstance jet, RuleTask<T> ruleTask) -> ruleTask.getRuleEvaluationResult(jet))
//                .setName("Evaluate rules");

        // TODO: not happy with the type assignment here, is it possible to lose the wildcards?
        StreamStage<? extends RuleEvaluationResult<T, ?>> taskResults = enrichedTasks.map(RuleTask::getRuleEvaluationResult)
                .setName("Evaluate rules");

        StreamStageWithKey<RuleEvaluationResult<T, Boolean>, String> groupedByItem =
                (StreamStageWithKey<RuleEvaluationResult<T, Boolean>, String>)
                        taskResults.groupingKey(RuleEvaluationResult::getItemId); // group all results for transaction

        StreamStage<Entry<String,RuleSetEvaluationResult<T,Boolean>>> aggregated = groupedByItem.rollingAggregate(
                ResultAggregator.anyTrue(RuleEvaluationResult::getValue))
                .setName("Aggregate ruleset results by set + item");

        // TODO: Now ruleset has own aggregator ... see PaymentRuleSet.aggregate() for example ...
        // TODO: but how to route to it here?

        // MAYBE TODO: map stage that takes the aggregation result and writes it into the stream item
        //             Would simplify the stages that follow

        // RER and now RSER carry the txnid, not full txn
        // either need to have full txn or be able to retrive it at this point ...

        //StreamStage ag2 = PaymentsRuleSet.addAggregationStage((StreamStageWithKey<RuleEvaluationResult, String>)groupedBySetWithinItem);
        StreamStage<Transaction> updatedItems = aggregated.map((Entry<String,RuleSetEvaluationResult<T,Boolean>> result) -> {
            Transaction txn = (Transaction) result.getValue().item; // TODO: ERROR: NPE on item
            boolean paymentsRulesetResult = result.getValue().getEvaluationResult();
            txn.setPaymentResult(paymentsRulesetResult);
            return txn;
        })
                .setName("Set Payment result into Transaction item");


        //StreamStageWithKey<RuleEvaluationResult<T>, String> groupedBySet = groupedByItem.groupingKey(RuleEvaluationResult::getRuleSetId); // group all rules within ruleset for a transaction

        // TODO: this exists in too many places, need to figure out right place to have it defined.
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.getNetworkConfig().addAddress(JetMain.IMDG_HOST);
        clientConfig.getGroupConfig().setName("dev").setPassword("ignored");

        updatedItems.drainTo(Sinks.remoteMapWithMerging("pendingTransactions", clientConfig,
                Transaction::getID,
                (Transaction txn) -> txn,
                (Transaction oldT, Transaction newT) -> {
                    oldT.setPaymentResult(newT.getPaymentResult());
                    return oldT;
                })).setName("Drain to remote pendingTransactions map with merge");

        // This is probably unnecessary now that pendingTransactions are getting updated in place
        //aggregated.drainTo(Sinks.remoteMap("resultMap", clientConfig));

//        // TODO: Drain to remote map ...  this may need a context factory approach as done with setting account info above
//        // TODO: If we're merging into pendingTransactions, it means we need to have already set the result into the
//        //    transaction .. when we evaluate the ruleset or as a separate stage following that.
//        // arg2:  DistributedFunction<? super E, ? extends K> toKeyFn
//        // arg3:  DistributedFunction<? super E, ? extends EntryProcessor<K, V>> toEntryProcessorFn
//        aggregated.drainTo(Sinks.remoteMapWithMerging("pendingTransactions", clientConfig,
//                /* toKeyFn */ (Map.Entry<String,RuleSetEvaluationResult> entry) -> { return entry.getKey(); },
//                /* toValueFn */ (Map.Entry<String,RuleSetEvaluationResult> entry) -> { return (Transaction) entry.getValue().item; },
//                /* mergeFn */ (Transaction oldItem, Transaction newItem) -> {
//                    // TODO: need to get payment result set!!!!
//                    oldItem.setPaymentResult(newItem.getPaymentResult());
//                    return oldItem;
//                }
//        ));
        // TODO: replace this logger with the results map, see code block above ...
        // (multiple drainTo's are fine, so no need to remove until other option verified to be working
//        aggregated.drainTo(Sinks.logger()).setName("Aggregated results for Payments ruleset");

        //taskResults.drainTo(Sinks.map("resultMap"));



        // Copy of above logic using fluent API -- before handling multiple rulesets
//        p.drawFrom(iqueueSource)
//                .flatMap((item) -> {
//                    List<RuleTask> rules = new ArrayList<>();
//                    for (Rule rule : localRuleSet) {    // this appears to be forcing attempted Serializable of BaseRuleEngine
//                        RuleTask<Transaction> task = new RuleTask(rule, item);
//                        rules.add(task);
//                    }
//                    return traverseIterator(rules.iterator());   // statically imported from Traversers
//                })
//                .filter(RuleTask::checkPreconditions)
//                .map(RuleTask::getRuleEvaluationResult)
//                .groupingKey(RuleEvaluationResult::getRuleId)
//                .rollingAggregate(ResultAggregator.anyTrue(RuleEvaluationResult::getValue) )
//                .drainTo(Sinks.map("resultMap"));


        // Build Graphite sink
        //Sink<TimestampedEntry> graphiteSink = buildGraphiteSink("127.0.0.1", 2004);

        // Drain all results to the Graphite sink
        //p.drainTo(graphiteSink, co2Emission, maxNoise, landingFlights, takingOffFlights)
        //        .setName("graphiteSink");


        return p;
    }

    // NOT IN USE
    private ContextFactory<JetInstance> getJetContext() {
        return ContextFactory.withCreateFn(jet -> { return Jet.newJetClient(); } );
    }

    private void createAndRunJob(Pipeline pipeline) {
        JobConfig config = new JobConfig();
        config.setName("Jet Rule Engine Demo");
        Job job = jet.newJob(pipeline, config);
        job.join();
        System.out.println("Job complete");
    }

    // Grafana & Pickle Integration
    /**
     * Sink implementation which forwards the items it receives to the Graphite.
     * Graphite's Pickle Protocol is used for communication between Jet and Graphite.
     *
     * @param host Graphite host
     * @param port Graphite port
     */
//    private static Sink<TimestampedEntry> buildGraphiteSink(String host, int port) {
//        return sinkBuilder("graphite", instance ->
//                new BufferedOutputStream(new Socket(host, port).getOutputStream()))
//                .<TimestampedEntry>receiveFn((bos, entry) -> {
//                    GraphiteMetric metric = new GraphiteMetric();
//                    metric.from(entry);
//
//                    PyString payload = cPickle.dumps(metric.getAsList(), 2);
//                    byte[] header = ByteBuffer.allocate(4).putInt(payload.__len__()).array();
//
//                    bos.write(header);
//                    bos.write(payload.toBytes());
//                })
//                .flushFn(BufferedOutputStream::flush)
//                .destroyFn(BufferedOutputStream::close)
//                .build();
//    }

}