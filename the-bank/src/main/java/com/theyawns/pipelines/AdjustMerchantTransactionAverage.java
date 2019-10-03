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
import com.hazelcast.jet.config.JobConfig;
import com.hazelcast.jet.datamodel.KeyedWindowResult;
import com.hazelcast.jet.pipeline.*;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.theyawns.Constants;
import com.theyawns.domain.payments.Merchant;
import com.theyawns.domain.payments.Transaction;
import com.theyawns.launcher.Launcher;
import com.theyawns.util.EnvironmentSetup;

import java.io.Serializable;
import java.util.ArrayList;

import static com.hazelcast.jet.Util.mapEventNewValue;
import static com.hazelcast.jet.Util.mapPutEvents;

/* Create a Jet client to the Jet cluster. Submit a job to the
 * Jet cluster that has an IMDG client to pull from IMDG cluster.
 */
public class AdjustMerchantTransactionAverage implements Serializable {

    private final static ILogger log = Logger.getLogger(AdjustMerchantTransactionAverage.class);

	private ClientConfig imdgClientConfig;
    private ClientConfig jetClientConfig;

    protected void init() {
    	this.imdgClientConfig = new XmlClientConfigBuilder().build();
    	this.jetClientConfig = new XmlClientConfigBuilder().build();

    	// IMDG
        if (this.imdgClientConfig.getNetworkConfig().getKubernetesConfig().isEnabled()
        		&& this.imdgClientConfig.getNetworkConfig().getAddresses().size() > 0) {
        	log.info("IMDG Remove listed server addresses in favour of Kubernetes discovery.");
        	this.imdgClientConfig.getNetworkConfig().setAddresses(new ArrayList<>());
        	this.imdgClientConfig.getGroupConfig().setName("BankInABox");
        	
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
        	this.jetClientConfig.getGroupConfig().setName("JetInABox");
        	
        	this.jetClientConfig.getNetworkConfig()
        	.getKubernetesConfig().setProperty("service-dns", EnvironmentSetup.JET_SERVICE);
        	this.jetClientConfig.getNetworkConfig()
        	.getKubernetesConfig().setProperty("service-port", EnvironmentSetup.JET_PORT);
        	
        	log.info("Jet Kubernetes config " + this.imdgClientConfig.getNetworkConfig().getKubernetesConfig());
        }
    }

    public void run() {
        this.init();
        JetInstance jet = null;

        try {
            Pipeline pipeline = buildPipeline();
        	System.out.println("Connect to Jet cluster temporarily");
        	jet = Jet.newJetClient(this.jetClientConfig);
        	System.out.println("Connected to Jet cluster temporarily as " + jet.getName());
        	JobConfig jobConfig = new JobConfig();
            jobConfig.setName("AdjustMerchantTxnAverage");
            System.out.println("Launching " + jobConfig);
            Job job = jet.newJob(pipeline, jobConfig);
            System.out.println("Launch " + job.getName() + ", status==" + job.getStatus());
        } catch (Exception e) {
        	System.out.println(this.getClass().getName() + " EXCEPTION " + e.getMessage());
        	e.printStackTrace(System.out);
        } finally {
        	if (jet != null) {
        		System.out.println("Disconnect from Jet " + jet.getName());
                jet.shutdown();
        	}
        }
    }

    private Pipeline buildPipeline() {

        try {
            Pipeline p = Pipeline.create();

            // Stage 1: Draw transactions from the mapJournal associated with the preAuth map
            StreamStage<Transaction> txns = p.drawFrom(Sources.<Transaction, String, Transaction>remoteMapJournal(Constants.MAP_PREAUTH, imdgClientConfig, mapPutEvents(),
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
                    imdgClientConfig,
                    /* toKeyFn */ Merchant::getMerchantId,
                    /* toValueFn */ Merchant::getObject,
                    /* mergeFn */ (Merchant o, Merchant n) -> {
                        // Don't log when average varies by less than one dollar
//                        if (Math.abs(o.getAvgTxnAmount() - n.getAvgTxnAmount()) > 1.00) {
//                            System.out.printf("Merchant %s average transaction amount updated from %.3f to %.3f\n", o.getMerchantId(),
//                                    o.getAvgTxnAmount(), n.getAvgTxnAmount());
//                        }
                        return n;
                    })).setName("Merge updated Merchant record back to IMDG merchantMap");

            return p;
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }
}
