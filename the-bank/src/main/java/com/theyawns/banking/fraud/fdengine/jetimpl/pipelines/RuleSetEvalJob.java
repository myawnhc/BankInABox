/*
 *  Copyright 2018-2021 Hazelcast, Inc
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.package com.theyawns.controller.launcher;
 */

package com.theyawns.banking.fraud.fdengine.jetimpl.pipelines;

import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.XmlClientConfigBuilder;
import com.hazelcast.jet.Jet;
import com.hazelcast.jet.JetInstance;
import com.hazelcast.jet.Job;
import com.hazelcast.jet.aggregate.AggregateOperations;
import com.hazelcast.jet.config.JetConfig;
import com.hazelcast.jet.config.JobConfig;
import com.hazelcast.jet.datamodel.WindowResult;
import com.hazelcast.jet.pipeline.*;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.theyawns.banking.Transaction;
import com.theyawns.banking.fraud.fdengine.imdgimpl.TransactionEvaluationResult;
import com.theyawns.banking.fraud.fdengine.imdgimpl.rulesets.LocationBasedRuleSet;
import com.theyawns.controller.Constants;
import com.theyawns.controller.config.EnvironmentSetup;
import com.theyawns.ruleengine.HasID;
import com.theyawns.ruleengine.ItemCarrier;
import com.theyawns.ruleengine.rulesets.RuleSet;
import com.theyawns.ruleengine.rulesets.RuleSetEvaluationResult;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RuleSetEvalJob<T extends HasID, R> implements Runnable {

    private final static ILogger log = Logger.getLogger(RuleSetEvalJob.class);

    private JetConfig    jetConfig;
    private ClientConfig imdgClientConfig;
    private ClientConfig jetClientConfig;

    private RuleSet<T,R> ruleSet; //  = new LocationBasedRuleSet(); // Transaction, Double

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
    private StreamSource<Map.Entry<String, ItemCarrier<T>>> getMapJournal() {
        StreamSource<Map.Entry<String, ItemCarrier<T>>> rjSource =
                Sources.mapJournal(Constants.MAP_WRAPPED_ITEMS,
                        JournalInitialPosition.START_FROM_OLDEST);
        return rjSource;
    }

//    private Pipeline buildCompactPipeline() {
//        Pipeline p = Pipeline.create();
//
//        // Stage 1: Read, wrap, and route
//        // 1a: Draw transactions from the mapJournal associated with the preAuth map
//        StreamStage<ItemCarrier<T>> carriers = p.readFrom(getMapJournal())
//                .withIngestionTimestamps()
//                .map(Map.Entry::getValue)
//                .setName("Draw transactions from PreAuth event journal")
//
//                // Stage 1b: Wrap with ItemCarrier
//                .map(ItemCarrier::new)
//                .map(ItemCarrier::setTimeEnqueuedForExecutor)
//                .setName("Wrap items with ItemCarrier");
//
//        // Stage 1c: Write to a Reliable Topic
//        carriers.writeTo(Sinks.reliableTopic("Items"))
//                .setName("Write to Reliable Topic");
//
//
//        // These steps are not needed for the pipeline to function, just
//        // using to track throughput.
//        // Output count every 30 seconds
//        SlidingWindowDefinition smallWindow = WindowDefinition.tumbling(TimeUnit.SECONDS.toMillis(30));
//        // Add a tumbling window over the stream, 30 second slices
//        StageWithWindow timingWindow = carriers.window(smallWindow);
//        // Aggregate and output the count of items in each 30 second slide
//        StreamStage<WindowResult<Long>> itemCount = timingWindow.aggregate(AggregateOperations.counting());
//        itemCount.writeTo(Sinks.logger(count -> count.result() / 30 + " TPS")); // Should see count of items
//
//        return p;
//    }

    /* Functionally identical to the compact pipeline; makes the types at each stage
     * explicit for instructional purposes
     */
    private Pipeline buildVerbosePipeline() {
        try {
            Pipeline p = Pipeline.create();

            // Stage 1: Read wrapped items
            // Draw transactions from the mapJournal associated with the preAuth map
            StreamStage<ItemCarrier<T>> carriers = p.readFrom(getMapJournal())
                    .withIngestionTimestamps()
                    .map(Map.Entry::getValue)
                    .setName("Read ItemCarriers");

            // ? Do we want to add another timestamp to the carrier here ?

            // Stage 2: Execute the ruleset.  At this point we're hoping that the
            // evaluation job is generic enough that it can be passed any RuleSet
            // and will handle it.

            StreamStage<RuleSetEvaluationResult<T,R>> results = carriers.map(ruleSet::apply)
                    .setName("Evaluate RuleSet " + ruleSet.getName());

            // Add result to accumulated results in TER, creating new when needed.
            // Concerned about race condition here.
            StreamStage<TransactionEvaluationResult<T>> ters = results.mapUsingIMap(Constants.MAP_RESULTS,
                    rser -> rser.getCarrier().getItemID(),
                    (RuleSetEvaluationResult rser, TransactionEvaluationResult<T> ter) -> {
                        if (ter == null) {
                            ter = TransactionEvaluationResult.newInstance(rser.getCarrier());
                            System.out.println("New TER for RSER " + rser.getCarrier().getItemID());
                        } else {
                            System.out.println("Adding RSER to existing TER " + rser.getCarrier().getItemID());
                        }
                        ter.addResult(rser);
                        return ter;
                    })
                    .setName("Add results to TER");

            // filter on ter.checkCompletion - write completed to completions (was Q, now map?)
            ters.filter( ter -> ter.checkForCompletion())
                    .writeTo(Sinks.map("Completions",
                            (TransactionEvaluationResult<T> ter) -> ter.getCarrier().getItemID(),
                            ter -> ter.getCarrier()));


            // TPS at this level counts all processed, whether completed or not.  In aggregation stage
            //     we only count completed, and that's the 'real' TPS count.
            // Output count every 30 seconds
            SlidingWindowDefinition smallWindow = WindowDefinition.tumbling(TimeUnit.SECONDS.toMillis(30));
            StageWithWindow timingWindow = ters.window(smallWindow);

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

        // THIS SHOULD BE PASSED IN - HARD CODED FOR TEST ONLY
        this.ruleSet = (RuleSet<T,R>) new LocationBasedRuleSet();

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
            jobConfig.setName("Evaluate RuleSet");
            Job job = jet.newJob(pipeline, jobConfig);
            log.info("Launched " + job.getName() + ", status==" + job.getStatus());
            job.join();
        } catch (Exception e) {
            log.severe(this.getClass().getName() + " EXCEPTION " + e.getMessage());
            e.printStackTrace(System.out);
        }
    }
}
