package com.theyawns.domain.payments;

import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.config.*;
import com.hazelcast.jet.Jet;
import com.hazelcast.jet.JetInstance;
import com.hazelcast.jet.Job;
import com.hazelcast.jet.config.JetConfig;
import com.hazelcast.jet.function.PredicateEx;
import com.hazelcast.jet.pipeline.*;
import com.theyawns.Constants;
import com.theyawns.IDSFactory;
import com.theyawns.launcher.BankInABoxProperties;
import com.theyawns.perfmon.PerfMonitor;

import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.hazelcast.jet.Util.mapEventNewValue;
import static com.hazelcast.jet.Util.mapPutEvents;

/** Base class for rules implemented in a Jet-centric RuleEngine.
 *
 *  As the demo design evolved, this class is no longer part of the main processing flow.
 *  It is still used by the DualLauncher config which is primarily used to help develop
 *  the PerfMonitor functionality
 */

public abstract class BaseRule implements Serializable {

    public static final String IMDG_HOST = "localhost";
    protected ClientConfig ccfg;
    protected JetConfig jc;

//    protected static final int SINK_PORT = 2004;
//    protected static String SINK_HOST;

    private PredicateEx<Transaction> filter = (PredicateEx<Transaction>) transaction -> true;

//    static {
//        System.setProperty("hazelcast.multicast.group", "228.19.18.20");
//        SINK_HOST = System.getProperty("SINK_HOST", "127.0.0.1");
//    }

    protected void init() {
        ManagementCenterConfig mcc = new ManagementCenterConfig();
        mcc.setEnabled(true);
        mcc.setUrl("http://localhost:8080/hazelcast-mancenter");

        ccfg = new ClientConfig();
        ccfg.getGroupConfig().setName("dev").setPassword("ignored");
        ccfg.getNetworkConfig().addAddress(IMDG_HOST);
        ccfg.getSerializationConfig().addDataSerializableFactory(101, new IDSFactory());


        jc = new JetConfig();
        Config hazelcastConfig = jc.getHazelcastConfig();
        // Avoid collision between the external IMDG (remoteMap) and the internal IMDG
        NetworkConfig networkConfig = hazelcastConfig.getNetworkConfig();
        //networkConfig.getJoin().getMulticastConfig().setEnabled(false);
        networkConfig.setPort(5710); // Group name defaults to Jet but port still defaults to 5701
        //hazelcastConfig.setManagementCenterConfig(mcc);

        // this appears to have no effect, probably because cluster already running when we connect
        EventJournalConfig ejc = new EventJournalConfig()
                .setMapName(Constants.MAP_PREAUTH)
                .setEnabled(true)
                .setCapacity(1000000);
        hazelcastConfig.getSerializationConfig().addDataSerializableFactory(101, new IDSFactory());
        hazelcastConfig.addEventJournalConfig(ejc);
        jc.setHazelcastConfig(hazelcastConfig);

    }

    public void setFilter(PredicateEx<Transaction> filter) {
        this.filter = filter;
    }

    protected StreamStage<TransactionWithRules> getEnrichedJournal(Pipeline p) {
        try {
            StreamStage<Transaction> txns = p.<Transaction>drawFrom(Sources.remoteMapJournal(Constants.MAP_PREAUTH, ccfg, mapPutEvents(),
                    mapEventNewValue(), JournalInitialPosition.START_FROM_OLDEST)).withIngestionTimestamps();

            StreamStage<Transaction> filtered = txns.filter(filter).setName("Filter on even/odd txn id");

            StreamStage<TransactionWithRules> enriched =
                    filtered.mapUsingContext(getJetContext(), (JetInstance jet, Transaction t) -> {
                                // This isn't working, we're starting a copy of the timer
                                //t.processingTime.start();
                                // TODO: rule name should not be hard coded here!
                                if (BankInABoxProperties.COLLECT_LATENCY_STATS) {
                                    PerfMonitor.getInstance().beginLatencyMeasurement(PerfMonitor.Platform.Jet, PerfMonitor.Scope.Processing,
                                            "CreditLimitRule", t.getItemID());
                                }
                                List<Job> activeJobs = jet.getJobs();
                                Set<String> rules = new HashSet<>();
                                for (Job j : activeJobs) {
                                    rules.add(j.getName());
                                }
                                //System.out.println("Adding " + activeJobs.size() + " rule ids to transaction " + t.getID() + "( acct " + t.getAccountNumber() + ")");
                                return new TransactionWithRules(t, rules);
                            }).setName("Enrich with currently active rule info");
            return enriched;
        } catch (Throwable t) {
            t.printStackTrace();
            return null;
        }
    }

    abstract Pipeline buildPipeline();

    protected ContextFactory<JetInstance> getJetContext() {
        return ContextFactory.withCreateFn(jet -> { return jet; } );
    }


    public void run(String jobname) {

        init();
        Pipeline p = buildPipeline();

        System.out.println("***********************************************");
        System.out.println("BaseRule.run Starting Jet instance");
        SerializationConfig sc = jc.getHazelcastConfig().getSerializationConfig();
        System.out.println(sc);
        System.out.println("MJC: " + jc.getHazelcastConfig().getMapEventJournalConfig("preAuth"));
        System.out.println("***********************************************");

        JetInstance jet = Jet.newJetInstance(jc);

        try {
            Job job = jet.newJob(p);
            job.getConfig().setName(jobname);
            job.join();
        } finally {
            jet.shutdown();
        }
    }

//    /**
//     * Sink implementation which forwards the items it receives to the Graphite.
//     * Graphite's Pickle Protocol is used for communication between Jet and Graphite.
//     *
//     * @param host Graphite host
//     * @param port Graphite port
//     */
//    protected static Sink<RuleExecutionResult> buildGraphiteSink(String host, int port) {
//        return sinkBuilder("graphite", instance ->
//                new BufferedOutputStream(new Socket(host, port).getOutputStream()))
//                .<RuleExecutionResult>receiveFn((bos, entry) -> {
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

//    /**
//     * A data transfer object for Graphite
//     */
//    protected static class GraphiteMetric {
//        PyString metricName;
//        PyInteger timestamp;
//        PyFloat metricValue;
//
//        protected GraphiteMetric() {
//        }
//
//        // Graph Transaction Results (approved/not)
//        protected void from(RuleExecutionResult rer) {
//            metricName = new PyString(replaceWhiteSpace(
//                    rer.ruleName  + "." +
//                            rer.result ));
//
//            timestamp = new PyInteger(getEpochSecond(
//                    rer.elapsedTime ));
//
//            metricValue = new PyFloat(1);
//        }
//
//        protected PyList getAsList() {
//            PyList list = new PyList();
//            PyTuple metric = new PyTuple(metricName, new PyTuple(timestamp, metricValue));
//            list.add(metric);
//            return list;
//        }
//
//        protected int getEpochSecond(long millis) {
//            return (int) Instant.ofEpochMilli(millis).getEpochSecond();
//        }
//
//        protected String replaceWhiteSpace(String string) {
//            return string.replace(" ", "_");
//        }
//    }

}
