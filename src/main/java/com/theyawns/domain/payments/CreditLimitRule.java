package com.theyawns.domain.payments;

import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.core.IMap;
import com.hazelcast.jet.Jet;
import com.hazelcast.jet.pipeline.ContextFactory;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.Sinks;
import com.hazelcast.jet.pipeline.StreamStage;
import com.theyawns.ruleengine.RuleEvaluationResult;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CreditLimitRule extends BaseRule implements Serializable {

    public static final String RULE_NAME = "CreditLimitRule";
    private IMap<String, List<RuleExecutionResult>> resultsMap;

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
                    return Jet.newJetClient(clientConfig).getMap("accountMap");
                });

        StreamStage<TransactionWithAccountInfo> txnsWithAccountInfo = enrichedJournal.mapUsingContext(contextFactory, (map, txn) -> {
            Account acct = map.get(txn.getAccountNumber());
            TransactionWithAccountInfo twa = new TransactionWithAccountInfo(txn);
            twa.setAccountInfo(acct);
            return twa;
        }).setName("Enrich transactions with Account info");

        // Common stage to all rules, can this move to base?  Will need to abstract TransactionWithAccountInfo to ? extends Transaction
        StreamStage<RuleEvaluationResult<Transaction,Boolean>> result = txnsWithAccountInfo.map(txn -> {
            RuleEvaluationResult<Transaction,Boolean> rer = new RuleEvaluationResult<Transaction,Boolean>(txn, RULE_NAME);
            rer.setEvaluationResult(process(txn));
            rer.setElapsed(System.currentTimeMillis() - txn.getIngestTime());
            return rer;
        }).setName("Evaluate Credit Limit rule");

        // Also common to all rules, try to move to base
        // Drain to remote (IMDG) results map, merging with any previous results
        ClientConfig clientConfig = new ClientConfig();
        clientConfig.getNetworkConfig().addAddress(JetMain.IMDG_HOST);
        clientConfig.getGroupConfig().setName("dev").setPassword("ignored");

        result.drainTo(Sinks.remoteMapWithMerging(
                "resultMap",
                clientConfig,
                (RuleEvaluationResult r) -> r.getItemId(),
                (RuleEvaluationResult r) -> new ArrayList<>(Arrays.asList(r)),
                (List<RuleEvaluationResult> o, List<RuleEvaluationResult> n) -> {
                    System.out.println("Merging result to resultsMap");
                    o.addAll(n);
                    return o;
                })).setName("Drain to IMDG results map");

        // TODO: drain to Graphite/Grafana

        result.drainTo(Sinks.logger());

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
