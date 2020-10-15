package com.theyawns.domain.payments.ruleengine;

import com.hazelcast.cluster.Member;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IExecutorService;
import com.theyawns.Constants;
import com.theyawns.executors.RuleSetExecutor;
import com.theyawns.ruleengine.RuleEngineController;
import com.theyawns.rulesets.LocationBasedRuleSet;
import com.theyawns.rulesets.MerchantRuleSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public class FraudDetectionEngine extends RuleEngineController {

    private HazelcastInstance hazelcast;
    private IExecutorService distributedES;
    private boolean verbose = true; // TODO: add setter

    // May remove/deprecate this as executor futures have not been an issue
    private List<Future<Exception>> executorFutures = new ArrayList<>();

    public FraudDetectionEngine(HazelcastInstance hz) {
        this.hazelcast = hz;
        this.distributedES = hz.getExecutorService("RuleSetExecutorService");

        LocationBasedRuleSet lbrules = new LocationBasedRuleSet();
        RuleSetExecutor locationBasedRuleExecutor = new RuleSetExecutor(Constants.QUEUE_LOCATION,
                lbrules, Constants.MAP_PPFD_RESULTS);
        locationBasedRuleExecutor.setVerbose(verbose);
        try {
            Map<Member, CompletableFuture<Exception>> futures = distributedES.submitToAllMembers(locationBasedRuleExecutor);
            executorFutures.addAll(futures.values());
        } catch (Throwable t) {
            t.printStackTrace();
        }
        super.addRuleSet(lbrules, hazelcast.getQueue(Constants.QUEUE_LOCATION));
        if (verbose)
            System.out.println("Submitted RuleSetExecutor for location rules to distributed executor service (all members)");

        //----------------
        MerchantRuleSet mrules = new MerchantRuleSet();
        RuleSetExecutor merchantRuleSetExecutor = new RuleSetExecutor(Constants.QUEUE_MERCHANT,
                mrules, Constants.MAP_PPFD_RESULTS);
        merchantRuleSetExecutor.setVerbose(verbose);
        try {
            Map<Member,CompletableFuture<Exception>> futures = distributedES.submitToAllMembers(merchantRuleSetExecutor);
            executorFutures.addAll(futures.values());
        } catch (Throwable t) {
            t.printStackTrace();
        }
        super.addRuleSet(mrules, hazelcast.getQueue(Constants.QUEUE_MERCHANT));
        if (verbose)
            System.out.println("Submitted RuleSetExecutor for merchant rules to distributed executor service (all members)");
    }

}
