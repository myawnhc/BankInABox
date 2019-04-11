package com.theyawns.domain.payments;

import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.Sinks;
import com.hazelcast.jet.pipeline.StreamStage;

public class CreditLimitRule extends BaseRule {

    @Override
    Pipeline buildPipeline() {
        Pipeline p = Pipeline.create();
        StreamStage<TransactionWithRules> enrichedJournal = getEnrichedJournal(p);

        // TODO: copy applicable processing stages from JetRuleEngine
        enrichedJournal.drainTo(Sinks.logger());

        return p;
    }

    public static void main(String[] args) {
        CreditLimitRule rule = new CreditLimitRule();
        rule.run();
    }
}
