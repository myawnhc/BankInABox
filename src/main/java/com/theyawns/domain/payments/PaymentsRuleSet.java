package com.theyawns.domain.payments;

import com.hazelcast.jet.Jet;
import com.hazelcast.jet.JetInstance;
import com.hazelcast.jet.pipeline.ContextFactory;
import com.theyawns.ruleengine.*;

import java.io.Serializable;
import java.util.HashSet;

/** RuleSet for Payment Processing
 *
 *  Includes any state required by the RuleSet to process the rules - in this case, the Account map
 *  TODO: Account Transaction History map will be needed for as-yet-uncoded rules
 *
 */
public class PaymentsRuleSet extends RuleSet<Transaction> implements Serializable {

    //private HazelcastInstance hazelcast;
    private Transaction transaction;

    public static final String RULESET_ID = "Payments Ruleset";


    //private IMap<String, Account> accountMap;

    public PaymentsRuleSet(Transaction txn) {
        //this.hazelcast = hz;
        this.transaction = txn;
        this.name = RULESET_ID;

        //accountMap = hz.getMap("accountMap");
        rules = new HashSet<>();
        rules.add(new CreditLimitRule(this));
        rules.add(new RandomRule(this));
        // TODO: more rules

    }

    @Override
    public Transaction getItem() {
        return transaction;
    }

    //@Override
    // Get errors here if we parameterize the RER to Transaction because the Engine just has a
    // type variable ... but there is nothing we need from the Transaction so this should work cleanly.
//    public static GeneralStage addAggregationStage(StreamStageWithKey<RuleEvaluationResult, String> input) {
//        // EVERYTHING will get routed here ... so we have to filter out results that don't belong
//        // to our ruleset
//        GeneralStage<RuleEvaluationResult> filtered =
//                input.filterUsingContext(emptyContext(),
//                        (Integer empty, String s, RuleEvaluationResult rer) -> {
//                            System.out.println("filtering " + rer.getRuleSetId());
//                            return rer.getRuleSetId().equals(RULESET_ID);
//                        }
//                );
//
//        return (GeneralStage) filtered.rollingAggregate(
//                ResultAggregator.anyTrue(RuleEvaluationResult<Transaction,Boolean>::getValue))
//                .setName("Aggregate ruleset results by set + item");
//    }

    private static ContextFactory<Integer> emptyContext() {
        return ContextFactory.withCreateFn(empty -> { return Integer.valueOf(0); });
    }

    private ContextFactory<JetInstance> getJetContext() {
        return ContextFactory.withCreateFn(jet -> { return Jet.newJetClient(); } );
    }


//    @Override
//    public AggregateOperation1<Transaction, ResultAccumulator, RuleSetEvaluationResult> getAggregator() {
//        //return ResultAggregator::anyTrue;
//        //return AggregatorProvider;
//        return null;
//    }

    class CreditLimitRule extends AbstractRule<Transaction,Boolean> {

        CreditLimitRule(RuleSet set) {
            this.ruleSet = set;
        }

        @Override
        public boolean checkPreconditions(Transaction streamItem) {
            return true;
        }

        @Override
        public RuleEvaluationResult<Transaction,Boolean> process(RuleTask task) {
            Transaction transaction = (Transaction) task.getStreamItem();
            assert task instanceof RuleTaskWithAccountInfo;
            Account account = ((RuleTaskWithAccountInfo)task).getAccountInfo();
            double projectedBalance = account.getBalance() + transaction.getAmount();
            RuleEvaluationResult<Transaction,Boolean> result =
                    new RuleEvaluationResult<Transaction,Boolean>("Credit limit check", RULESET_ID, transaction);
            result.setEvaluationResult(projectedBalance > account.getCreditLimit());
            return result;
            // TODO: set processing time
        }

        @Override
        public RuleTaskWithAccountInfo createTask() {
            RuleTaskWithAccountInfo task = new RuleTaskWithAccountInfo(this, name, getItem());
            return task;
        }

        @Override
        public String toString() {
            return "CreditLimitRule";
        }
    }

    // TODO: account status rule

}
