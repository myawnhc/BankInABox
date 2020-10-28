package com.theyawns.banking.payments.rules;

import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.jet.Jet;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.ServiceFactories;
import com.hazelcast.jet.pipeline.ServiceFactory;
import com.hazelcast.jet.pipeline.Sinks;
import com.hazelcast.jet.pipeline.StreamStage;
import com.hazelcast.map.IMap;
import com.theyawns.banking.Account;
import com.theyawns.banking.Transaction;
import com.theyawns.banking.fraud.fdengine.jetimpl.holding.TransactionWithAccountInfo;
import com.theyawns.banking.fraud.fdengine.jetimpl.holding.TransactionWithRules;
import com.theyawns.controller.Constants;
import com.theyawns.controller.IDSFactory;
import com.theyawns.controller.launcher.BankInABoxProperties;
import com.theyawns.ruleengine.jetimpl.rules.BaseRule;
import com.theyawns.ruleengine.jetimpl.rules.RuleEvaluationResult;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Implementation of a Credit Limit check as a Jet pipeline.
 *  The same basic functionality is implemented as an IMDG EntryProcessor in entryprocessors.CreditLimitCheck.
 *
 */
@Deprecated
public class CreditLimitRule extends BaseRule implements Serializable {

    public static final String IMDG_HOST = "localhost";
    public static final String RULE_NAME = "CreditLimitRule";
    //private IMap<String, List<RuleExecutionResult>> resultsMap;

    @Override
    public Pipeline buildPipeline() {

        Pipeline p = Pipeline.create();
        StreamStage<TransactionWithRules> enrichedJournal = getEnrichedJournal(p);

        // Rule-specific enrichment -  add Account info to get access to the Credit Limit for the account
//        ServiceFactory<IMap<String, Account>, ?> serviceFactory =
//                ServiceFactory.withCreateContextFn(x -> {
//                    ClientConfig clientConfig = new ClientConfig();
//                    clientConfig.getNetworkConfig().addAddress(IMDG_HOST);
//                    clientConfig.setClusterName("dev");
//                    clientConfig.getSerializationConfig().addDataSerializableFactory(101, new IDSFactory());
//                    return Jet.newJetClient(clientConfig).getMap("accountMap");
//                });

        // Alternate 4.1 way of creating service factory - is one preferred over the other?
        ServiceFactory<?, IMap<String, Account>> factory2 =
                ServiceFactories.sharedService(unusedctx -> {
                    ClientConfig clientConfig = new ClientConfig();
                    clientConfig.getNetworkConfig().addAddress(IMDG_HOST);
                    clientConfig.setClusterName("dev");
                    clientConfig.getSerializationConfig().addDataSerializableFactory(101, new IDSFactory());
                    return Jet.newJetClient(clientConfig).getMap("accountMap");
                });

        StreamStage<TransactionWithAccountInfo> txnsWithAccountInfo = enrichedJournal.mapUsingService(factory2, (map, txn) -> {
            Account acct = map.get(txn.getAccountNumber());
            TransactionWithAccountInfo twa = new TransactionWithAccountInfo(txn);
            twa.setAccountInfo(acct);
            //System.out.println("CreditLimitRule: Enrich account info");
            return twa;
        }).setName("Enrich transactions with Account info");

//        // was working before 4.0, haven't figured out how to transition ContextFactory to ServiceFactory
//        StreamStage<TransactionWithAccountInfo> txnsWithAccountInfo = enrichedJournal.mapUsingService(serviceFactory, (map, txn) -> {
//            Account acct = map.get(txn.getAccountNumber());
//            TransactionWithAccountInfo twa = new TransactionWithAccountInfo(txn);
//            twa.setAccountInfo(acct);
//            //System.out.println("CreditLimitRule: Enrich account info");
//            return twa;
//        }).setName("Enrich transactions with Account info");

        // Common stage to all rules, can this move to base?  Will need to abstract TransactionWithAccountInfo to ? extends Transaction
        StreamStage<RuleEvaluationResult<Transaction,Boolean>> result = txnsWithAccountInfo.map(txn -> {
            RuleEvaluationResult<Transaction,Boolean> rer = new RuleEvaluationResult<Transaction,Boolean>(txn, RULE_NAME);
            rer.setEvaluationResult(process(txn));
            //rer.setElapsed(System.currentTimeMillis() - txn.getIngestTime());
            //System.out.println("CreditLimitRule: map to result");  // OK this far
            return rer;
        }).setName("Evaluate Credit Limit rule");

        // Also common to all rules, try to move to base
        // Drain to remote (IMDG) results map, merging with any previous results
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.getNetworkConfig().addAddress(IMDG_HOST);
        clientConfig.setClusterName("dev");
        clientConfig.getSerializationConfig().addDataSerializableFactory(101, new IDSFactory());

        // When done inline, get class not loaded, so pull out of lambda
        boolean logPerf = BankInABoxProperties.COLLECT_LATENCY_STATS;

        try {
            result.writeTo(Sinks.remoteMapWithMerging(
                    Constants.MAP_RESULTS, // NOTE: this is different than the currently active resultMap, so
                       // if this class is resurrected we'll need to decide where this output really goes ...
                    clientConfig,
                    (RuleEvaluationResult r) -> r.getItemId(),
                    (RuleEvaluationResult r) -> new ArrayList<>(Arrays.asList(r)),
                    (List<RuleEvaluationResult> o, List<RuleEvaluationResult> n) -> {
                        //System.out.println("CreditLimitRule: Merging result to resultsMap"); // NEVER SEEN
                        o.addAll(n);
                        // following lines just for performance statistics collection
                        Transaction t = (Transaction) o.get(0).getItem();
                        //t.processingTime.stop();
                        return o;
                    })).setName("Drain to IMDG results map");

//            result.drainTo(Sinks.logger((rer) -> {
//                if (logPerf) {
//                    System.out.println(">>> call end Jet Processing from secondary drainTo");
//                    PerfMonitor.getInstance().endLatencyMeasurement(PerfMonitor.Platform.Jet,
//                            PerfMonitor.Scope.Processing, "CreditLimitRule", rer.getItemId());
//                    //PerfMonitor.getInstance().recordTransaction("Jet", t);
//                    System.out.println("<<< call end Jet Processing from secondary drainTo");
//                }
//                return null;
//            }));
        } catch (Exception e) {
            e.printStackTrace();
        }

        // TODO: drain to Graphite/Grafana

        //result.drainTo(Sinks.logger());

        return p;
    }

    // false = transaction should be rejected, over limit
    // true = transaction should be approved, <= limit
    // TODO: if processing stage moves to base, make an abstract base version of this which is overridden here
    //       with a more specific subtype (base argument type would be <T extends Transaction>
    private static boolean process(TransactionWithAccountInfo transaction) {
        //System.out.println("Evaluating rule");
        Account account = transaction.getAccountInfo();
        double projectedBalance = account.getBalance() + transaction.getAmount();
        if (projectedBalance > account.getCreditLimit())
            return false;

        return true;
    }

    public static void main(String[] args) {
        CreditLimitRule rule = new CreditLimitRule();
        rule.run(RULE_NAME);
    }
}
