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

package com.theyawns.banking.fraud.fdengine;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IExecutorService;
import com.hazelcast.replicatedmap.ReplicatedMap;
import com.theyawns.banking.fraud.fdengine.imdgimpl.executors.RuleSetExecutor;
import com.theyawns.banking.fraud.fdengine.imdgimpl.rulesets.LocationBasedRuleSet;
import com.theyawns.banking.fraud.fdengine.imdgimpl.rulesets.MerchantRuleSet;
import com.theyawns.controller.Constants;
import com.theyawns.ruleengine.RuleEngineRoutingController;
import com.theyawns.ruleengine.RuleSetRoutingInfo;
import com.theyawns.ruleengine.rules.Rule;
import com.theyawns.ruleengine.rulesets.RuleSet;

// Really just a helper class for the launcher that collects together several
// executors that need to be instantiated and sent into the cluster for execution
// This sets up the initial population of RuleSets; although not presently implemented
// the goal is to have a REST service (via RuleEngineRoutingController) that allows
// rulesets to be dynamically added, removed, or updated.
public class FraudDetectionEngine {

    //private HazelcastInstance hazelcast;
    private IExecutorService distributedES;
    private ReplicatedMap<String,RuleSetRoutingInfo> routingInfoMap;
    private boolean verbose = true; // should add setter & toggle from launcher
    //private RuleEngineRoutingController<T> router;

    public FraudDetectionEngine(HazelcastInstance hz) {
        distributedES = hz.getExecutorService("RuleSetExecutorService");
        routingInfoMap = hz.getReplicatedMap(Constants.MAP_RS_ROUTING);

        //----------------
        // The RERC will arm a local listener on PreAuth map on each node
        // to route incoming transactions to the applicable rulesets
        RuleEngineRoutingController rerc = new RuleEngineRoutingController();
        // TODO: set map & listener so rerc can register listener without
        // having any domain-specific knowledge of PA & PAML
        distributedES.executeOnAllMembers(rerc);

        //----------------
        System.out.println("FDE registering Rulesets with RERC");
        LocationBasedRuleSet lbrules = new LocationBasedRuleSet();
        RuleSetExecutor locationBasedRuleExecutor = new RuleSetExecutor(Constants.QUEUE_LOCATION,
                lbrules, Constants.MAP_RESULTS);
        locationBasedRuleExecutor.setVerbose(verbose);
        distributedES.submitToAllMembers(locationBasedRuleExecutor);
        addRuleSet(lbrules, Constants.QUEUE_LOCATION);
        if (verbose)
            System.out.println("Submitted RuleSetExecutor for location rules to distributed executor service (all members)");

        //----------------
        MerchantRuleSet mrules = new MerchantRuleSet();
        RuleSetExecutor merchantRuleSetExecutor = new RuleSetExecutor(Constants.QUEUE_MERCHANT,
                mrules, Constants.MAP_RESULTS);
        merchantRuleSetExecutor.setVerbose(verbose);
        distributedES.submitToAllMembers(merchantRuleSetExecutor);
        addRuleSet(mrules, Constants.QUEUE_MERCHANT);
        if (verbose)
            System.out.println("Submitted RuleSetExecutor for merchant rules to distributed executor service (all members)");
    }

    private void addRuleSet(RuleSet set, String queue) {
        RuleSetRoutingInfo info = new RuleSetRoutingInfo(set, queue);
        //knownRuleSets.put(set.getName(), info);
        routingInfoMap.put(set.getName(), info);
        System.out.println("FDE: Added RuleSet " + set.getName() + " to routing info");
    }
}
