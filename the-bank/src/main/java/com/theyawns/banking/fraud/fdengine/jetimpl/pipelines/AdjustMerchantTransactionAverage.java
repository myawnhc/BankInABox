package com.theyawns.banking.fraud.fdengine.jetimpl.pipelines;

import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.XmlClientConfigBuilder;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.jet.Jet;
import com.hazelcast.jet.JetInstance;
import com.hazelcast.jet.Job;
import com.hazelcast.jet.aggregate.AggregateOperations;
import com.hazelcast.jet.config.JetConfig;
import com.hazelcast.jet.config.JobConfig;
import com.hazelcast.jet.datamodel.KeyedWindowResult;
import com.hazelcast.jet.pipeline.JournalInitialPosition;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.Sinks;
import com.hazelcast.jet.pipeline.Sources;
import com.hazelcast.jet.pipeline.StreamSource;
import com.hazelcast.jet.pipeline.StreamStage;
import com.hazelcast.jet.pipeline.WindowDefinition;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.hazelcast.replicatedmap.ReplicatedMap;
import com.theyawns.controller.Constants;
import com.theyawns.banking.Merchant;
import com.theyawns.banking.Transaction;
import com.theyawns.controller.config.EnvironmentSetup;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Map;

/* Create a Jet client to the Jet cluster. Submit a job to the
 * Jet cluster that has an IMDG client to pull from IMDG cluster.
 */
public class AdjustMerchantTransactionAverage implements Serializable, HazelcastInstanceAware {
	private static final long serialVersionUID = 1L;

	private final static ILogger log = Logger.getLogger(AdjustMerchantTransactionAverage.class);

	private ClientConfig imdgClientConfig;
    private ClientConfig jetClientConfig;

    private JetConfig jetConfig;

    private HazelcastInstance imdg;

    private boolean externalJetCluster = true; // may be overridden by bib.jetmode property or CL arg

    public void setJetClusterIsExternal(boolean external) {
        externalJetCluster = external;
    }

    protected void init() {
        //log.info("BEGIN AdjustMerchantTransactionAverage init()");
    	this.imdgClientConfig = new XmlClientConfigBuilder().build();
    	this.jetClientConfig = new XmlClientConfigBuilder().build();

    	jetClientConfig.getNetworkConfig().getAddresses().clear();
    	jetClientConfig.getNetworkConfig().addAddress("127.0.0.1:5710");
    	// This is just temporary until we move to client-server Jet usage
    	this.jetConfig = new JetConfig();

    	// IMDG
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
        //log.info("END AdjustMerchantTransactionAverage init()");

    }

    public void run() {
        this.init();
        JetInstance jet = null;

        try {
            Pipeline pipeline = buildPipeline();

            if (pipeline == null) {
                log.severe("***** Pipeline construction failed, AMTA will exit");
                return;
            }

            if (externalJetCluster) {
                log.info("Setting JetClientConfig to point to JetInABox and creating new client");
                jetClientConfig.setClusterName("JetInABox");
                jet = Jet.newJetClient(this.jetClientConfig);
                log.info("Connected to Jet cluster in client/server mode" + jet.getName());
            } else {
                jet = Jet.newJetInstance(jetConfig);
                log.info("Connected to Jet cluster in embedded mode " + jet.getName());
            }

        	JobConfig jobConfig = new JobConfig();
            jobConfig.setName("AdjustMerchantTransactionAverage");
            Job job = jet.newJob(pipeline, jobConfig);
            log.info("Launched " + job.getName() + ", status==" + job.getStatus());
        } catch (Exception e) {
        	log.severe(this.getClass().getName() + " EXCEPTION " + e.getMessage());
        	e.printStackTrace(System.out);
        }
    }

    private Pipeline buildPipeline() {

        try {
            Pipeline p = Pipeline.create();

            // Stage 1: Draw transactions from the mapJournal associated with the preAuth map
            StreamSource<Map.Entry<String, Transaction>> rjSource =
                    Sources.remoteMapJournal(Constants.MAP_PREAUTH,
                        imdgClientConfig,
                        JournalInitialPosition.START_FROM_OLDEST);

            StreamStage<Transaction> txns = p.readFrom(rjSource)
                    .withIngestionTimestamps()
                    .map( entry -> entry.getValue())
                    .setName("Draw transactions from PreAuth event journal");

            // Have a very large window to improve accuracy over time, but slide over shorter intervals to get initial updates flowing earlier
            WindowDefinition window = WindowDefinition.sliding(100000, 5000);

            // PEEK here shows valid looking merchant ids
            StreamStage<KeyedWindowResult<String, Double>> merchantAverages = txns.window(window)
                    .groupingKey(Transaction::getMerchantId)
                    .aggregate(
                            AggregateOperations.averagingDouble(
                                    Transaction::getAmount))
                    .setName("Aggregate average transaction amount by merchant");

            final ReplicatedMap<String, Merchant> merchantMap = imdg.getReplicatedMap(Constants.MAP_MERCHANT);
            StreamStage<Merchant> updatedMerchants = merchantAverages.mapUsingReplicatedMap(merchantMap,
                    // lookupKeyFn takes KeyedWindowResult from previous stage, returns MerchantID String
                    KeyedWindowResult::getKey,
                    // mapFn takes KeyedWindowResult, Merchant corresponding to key returned by keyFn
                    // returns Merchant with average transaction amount updated by data in the KWR value
                    (kwr, merchant) -> {
                        // Saw a single NPE here in 500K transactions, add check until we understand why and
                        // fix at the source of the error
                        if (kwr != null && merchant != null)
                            merchant.setAvgTxnAmount(kwr.getValue());
                        return merchant;
            }).setName("Retrieve merchant record from IMDG and update average txn amt");

            updatedMerchants.writeTo(Sinks.remoteMapWithMerging("merchantMap",
                    imdgClientConfig,
                    /* toKeyFn */ Merchant::getMerchantId,
                    /* toValueFn */ Merchant::getObject,
                    /* mergeFn */ (Merchant o, Merchant n) -> {
                        if (false) { // This logging has a HUGE negative impact on latency !!!
                            // Don't log when average varies by less than one dollar
                            if (Math.abs(o.getAvgTxnAmount() - n.getAvgTxnAmount()) > 1.00) {
                                System.out.printf("Merchant %s average transaction amount updated from %.3f to %.3f\n", o.getMerchantId(),
                                        o.getAvgTxnAmount(), n.getAvgTxnAmount());
                            }
                        }
                        return n;
                    })).setName("Merge updated Merchant record back to IMDG merchantMap");

            return p;
        } catch (Throwable e) {
            log.severe("****** Exception in AMTA.buildPipeline", e);
            return null;
        }
    }

    // This is not called automatically by HZ core, but explicitly by the Launcher
    @Override
    public void setHazelcastInstance(HazelcastInstance hazelcastInstance) {
        //log.info("**** injected HZI is from cluster " + hazelcastInstance.getCluster().toString());
        imdg = hazelcastInstance;
    }
}
