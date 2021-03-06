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

package com.theyawns.ruleengine.jetimpl.rules;

import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.config.Config;
import com.hazelcast.config.EventJournalConfig;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.config.SerializationConfig;
import com.hazelcast.function.PredicateEx;
import com.hazelcast.jet.Jet;
import com.hazelcast.jet.JetInstance;
import com.hazelcast.jet.Job;
import com.hazelcast.jet.config.JetConfig;
import com.hazelcast.jet.pipeline.JournalInitialPosition;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.ServiceFactories;
import com.hazelcast.jet.pipeline.ServiceFactory;
import com.hazelcast.jet.pipeline.Sources;
import com.hazelcast.jet.pipeline.StreamSource;
import com.hazelcast.jet.pipeline.StreamStage;
import com.theyawns.banking.Transaction;
import com.theyawns.banking.fraud.fdengine.jetimpl.holding.TransactionWithRules;
import com.theyawns.controller.Constants;
import com.theyawns.controller.IDSFactory;

import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Base class for rules implemented in a Jet-centric RuleEngine.
 *
 *  As the demo design evolved, this class is no longer part of the main processing flow.
 *  It is still used by the DualLauncher config which is primarily used to help develop
 *  the PerfMonitor functionality
 */

@Deprecated
public abstract class BaseRule implements Serializable {

    public static final String IMDG_HOST = "localhost";
    protected ClientConfig ccfg;
    protected JetConfig jc;

    private PredicateEx<Transaction> filter = (PredicateEx<Transaction>) transaction -> true;

    protected void init() {

        ccfg = new ClientConfig();
        ccfg.setClusterName("dev");
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
                //.setMapName(Constants.MAP_PREAUTH)
                .setEnabled(true)
                .setCapacity(1000000);
        hazelcastConfig.getSerializationConfig().addDataSerializableFactory(101, new IDSFactory());
        hazelcastConfig.getMapConfig(Constants.MAP_PREAUTH).setEventJournalConfig(ejc);
        jc.setHazelcastConfig(hazelcastConfig);
    }

    public void setFilter(PredicateEx<Transaction> filter) {
        this.filter = filter;
    }

    protected StreamStage<TransactionWithRules> getEnrichedJournal(Pipeline p) {
        try {
            StreamSource<Map.Entry<String, Transaction>> rjSource =
                    Sources.remoteMapJournal(Constants.MAP_PREAUTH,
                    ccfg,
                    JournalInitialPosition.START_FROM_OLDEST);

            StreamStage<Transaction> txns = p.readFrom(rjSource)
                    .withoutTimestamps()
                    .map( entry -> entry.getValue());


            StreamStage<Transaction> filtered = txns.filter(filter).setName("Filter on even/odd txn id");

            StreamStage<TransactionWithRules> enriched =
                    filtered.mapUsingService(getJetServiceFactory(), (JetInstance jet, Transaction t) -> {
                                // This isn't working, we're starting a copy of the timer
                                //t.processingTime.start();

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

    protected abstract Pipeline buildPipeline();

    protected ServiceFactory<?, JetInstance> getJetServiceFactory() {
        ServiceFactory<?, JetInstance> factory =
                ServiceFactories.sharedService(context -> {
                    return context.jetInstance();
                });
        return factory;
    }

    public void run(String jobname) {

        init();
        Pipeline p = buildPipeline();

        System.out.println("***********************************************");
        System.out.println("BaseRule.run Starting Jet instance");
        SerializationConfig sc = jc.getHazelcastConfig().getSerializationConfig();
        System.out.println(sc);
        System.out.println("MJC: " + jc.getHazelcastConfig().getMapConfig("preAuth").getEventJournalConfig());
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
}
