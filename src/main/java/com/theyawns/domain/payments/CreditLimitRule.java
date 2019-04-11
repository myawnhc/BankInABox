package com.theyawns.domain.payments;

import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.core.IMap;
import com.hazelcast.jet.Jet;
import com.hazelcast.jet.pipeline.ContextFactory;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.Sinks;
import com.hazelcast.jet.pipeline.StreamStage;

public class CreditLimitRule extends BaseRule {

    public static final String RULE_NAME = "CreditLimitRule";

    @Override
    Pipeline buildPipeline() {

        ClientConfig clientConfig = new ClientConfig();
        clientConfig.getNetworkConfig().addAddress(JetMain.IMDG_HOST);
        clientConfig.getGroupConfig().setName("dev").setPassword("ignored");

        Pipeline p = Pipeline.create();
        StreamStage<TransactionWithRules> enrichedJournal = getEnrichedJournal(p);

        // Rule-specific enrichment -  add Account info to get access to the Credit Limit for the account
        ContextFactory<IMap<String, Account>> contextFactory =
                ContextFactory.withCreateFn(x -> {
                    return Jet.newJetClient(clientConfig).getMap("accountMap");
                });

        StreamStage<TransactionWithAccountInfo> txnsWithAccountInfo = enrichedJournal.mapUsingContext(contextFactory, (map, txn) -> {
           Account acct = map.get(txn.getAccountNumber());
           TransactionWithAccountInfo twa = new TransactionWithAccountInfo(txn);
           twa.setAccountInfo(acct);
           return twa;
        })
                .setName("Enrich transactions with Account info");

        StreamStage<RuleExecutionResult> result = txnsWithAccountInfo.map( txn -> {
            RuleExecutionResult rer = new RuleExecutionResult(txn, RULE_NAME);
            rer.setResult(process(txn));
            rer.setElapsed(System.currentTimeMillis() - txn.getIngestTime());
            return rer;
        });

        // TODO: drain to results map

//        result.drainTo(Sinks.remoteMapWithMerging("resultMap", clientConfig,
//                /* toKeyFn */ RuleExecutionResult::getTransactionID,
//                /* toValueFn */ (RuleExecutionResult r) -> r,
//                /* mergeFn */ (List<RuleExecutionResult> original, List<RuleExecutionResult> modified) -> {
//                    original.add(r);
//                    return original;
//                })).setName("Drain to remote results map with merge");



        // TODO: drain to Grafana

        result.drainTo(Sinks.logger());

        return p;
    }

    // false = transaction should be rejected, over limit
    // true = transaction should be approved, <= limit
    private boolean process(TransactionWithAccountInfo transaction) {
        Account account = transaction.getAccountInfo();
        double projectedBalance = account.getBalance() + transaction.getAmount();
        if (projectedBalance > account.getCreditLimit())
            return false;

        return true;
    }

    public static void main(String[] args) {
        CreditLimitRule rule = new CreditLimitRule();
        rule.run();
    }
}
