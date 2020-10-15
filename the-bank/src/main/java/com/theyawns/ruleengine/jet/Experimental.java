package com.theyawns.ruleengine.jet;

import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.XmlClientConfigBuilder;
import com.hazelcast.jet.Jet;
import com.hazelcast.jet.JetInstance;
import com.hazelcast.jet.Job;
import com.hazelcast.jet.aggregate.AggregateOperations;
import com.hazelcast.jet.config.JetConfig;
import com.hazelcast.jet.config.JobConfig;
import com.hazelcast.jet.pipeline.JournalInitialPosition;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.Sinks;
import com.hazelcast.jet.pipeline.Sources;
import com.hazelcast.jet.pipeline.StreamSource;
import com.hazelcast.jet.pipeline.StreamStage;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.theyawns.Constants;
import com.theyawns.domain.payments.Transaction;
import com.theyawns.util.EnvironmentSetup;

import java.util.ArrayList;
import java.util.Map;

public class Experimental {

    private final static ILogger log = Logger.getLogger(Experimental.class);

    private JetConfig    jetConfig;
    private ClientConfig imdgClientConfig;
    private ClientConfig jetClientConfig;

    boolean externalJetCluster = true; // vs. embedded

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

    }

    private StreamSource<Map.Entry<String, Transaction>> getMapJournal() {
        StreamSource<Map.Entry<String, Transaction>> rjSource =
                Sources.remoteMapJournal(Constants.MAP_PREAUTH,
                        imdgClientConfig,
                        JournalInitialPosition.START_FROM_OLDEST);
        return rjSource;
    }

    private Pipeline buildCompactPipeline() {
        Pipeline p = Pipeline.create();

        p.readFrom(getMapJournal())
                .withIngestionTimestamps()
                .map(entry->entry.getValue())
                .setName("Draw transactions from PreAuth event journal")
                .rollingAggregate(AggregateOperations.counting())
                .writeTo(Sinks.logger());
        return p;
    }


    // work out the details here, then shorten to compact version above
    private Pipeline buildVerbosePipeline() {
        try {
            Pipeline p = Pipeline.create();

            // Stage 1: Draw transactions from the mapJournal associated with the preAuth map
            StreamStage<Transaction> txns = p.readFrom(getMapJournal())
                    .withIngestionTimestamps()
                    .map( entry -> entry.getValue())
                    .setName("Draw transactions from PreAuth event journal");

            txns.rollingAggregate(AggregateOperations.counting())
                    .writeTo(Sinks.logger()); // Should see count of items



            return p;
        } catch (Throwable t) {
            t.printStackTrace();
            return null;
        }
    }

    public void run() {
        this.init();
        JetInstance jet = null;

        try {
            Pipeline pipeline = buildVerbosePipeline();

            if (pipeline == null) {
                log.severe("***** Pipeline construction failed, Jet RE will exit");
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
}
