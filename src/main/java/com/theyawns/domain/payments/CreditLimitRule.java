package com.theyawns.domain.payments;

import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.core.IMap;
import com.hazelcast.jet.Jet;
import com.hazelcast.jet.pipeline.ContextFactory;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.Sinks;
import com.hazelcast.jet.pipeline.StreamStage;
import com.theyawns.Constants;
import com.theyawns.IDSFactory;
import com.theyawns.launcher.BankInABoxProperties;
import com.theyawns.perfmon.PerfMonitor;
import com.theyawns.ruleengine.RuleEvaluationResult;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Implementation of a Credit Limit check as a Jet pipeline.
 *  The same basic functionality is implemented as an IMDG EntryProcessor in entryprocessors.CreditLimitCheck.
 *
 */
public class CreditLimitRule extends BaseRule implements Serializable {

    public static final String RULE_NAME = "CreditLimitRule";
    //private IMap<String, List<RuleExecutionResult>> resultsMap;

    @Override
    Pipeline buildPipeline() {

        Pipeline p = Pipeline.create();
        StreamStage<TransactionWithRules> enrichedJournal = getEnrichedJournal(p);

        // Rule-specific enrichment -  add Account info to get access to the Credit Limit for the account
        ContextFactory<IMap<String, Account>> contextFactory =
                ContextFactory.withCreateFn(x -> {
                    ClientConfig clientConfig = new ClientConfig();
                    clientConfig.getNetworkConfig().addAddress(JetMain.IMDG_HOST);
                    clientConfig.getGroupConfig().setName("dev").setPassword("ignored");
                    clientConfig.getSerializationConfig().addDataSerializableFactory(101, new IDSFactory());
                    return Jet.newJetClient(clientConfig).getMap("accountMap");
                });

        StreamStage<TransactionWithAccountInfo> txnsWithAccountInfo = enrichedJournal.mapUsingContext(contextFactory, (map, txn) -> {
            Account acct = map.get(txn.getAccountNumber());
            TransactionWithAccountInfo twa = new TransactionWithAccountInfo(txn);
            twa.setAccountInfo(acct);
            //System.out.println("CreditLimitRule: Enrich account info");
            return twa;
        }).setName("Enrich transactions with Account info");

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
        clientConfig.getNetworkConfig().addAddress(JetMain.IMDG_HOST);
        clientConfig.getGroupConfig().setName("dev").setPassword("ignored");
        clientConfig.getSerializationConfig().addDataSerializableFactory(101, new IDSFactory());

        // When done inline, get class not loaded, so pull out of lambda
        boolean logPerf = BankInABoxProperties.COLLECT_LATENCY_STATS;

        try {
            result.drainTo(Sinks.remoteMapWithMerging(
                    Constants.MAP_RESULT,
                    clientConfig,
                    (RuleEvaluationResult r) -> r.getItemId(),
                    (RuleEvaluationResult r) -> new ArrayList<>(Arrays.asList(r)),
                    (List<RuleEvaluationResult> o, List<RuleEvaluationResult> n) -> {
                        //System.out.println("CreditLimitRule: Merging result to resultsMap"); // NEVER SEEN
                        o.addAll(n);
                        // following lines just for performance statistics collection
                        Transaction t = (Transaction) o.get(0).getItem();
                        //t.processingTime.stop();
                        if (logPerf) {
                            //System.out.println(">>> CreditLimitRule call end Jet Processing from drainTo");
                            PerfMonitor.getInstance().endLatencyMeasurement(PerfMonitor.Platform.Jet,
                                    PerfMonitor.Scope.Processing, "CreditLimitRule", t.getID());
                            //PerfMonitor.getInstance().recordTransaction("Jet", t);
                            //System.out.println("<<< CreditLimitRule call end Jet Processing from drainTo");
                        } else {
                            //System.out.println("CreditLimitRule omitted call to end Jet Processing due to hang");
                        }
                        // end perf collection
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
