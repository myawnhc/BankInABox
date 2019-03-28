package com.theyawns.domain.payments;

import com.theyawns.ruleengine.AbstractRule;
import com.theyawns.ruleengine.RuleEvaluationResult;
import com.theyawns.ruleengine.RuleSet;
import com.theyawns.ruleengine.RuleTask;

import java.io.Serializable;
import java.util.HashSet;

/** RuleSet for Payment Processing
 *
 *  Includes any state required by the RuleSet to process the rules - in this case, the Account map
 *  TODO: Account Transaction History map will be needed for as-yet-uncoded rules
 *
 */
public class FraudRuleSet extends RuleSet<Transaction> implements Serializable {

    //private HazelcastInstance hazelcast;
    private Transaction transaction;

    public static final String RULESET_ID = "Fraud Ruleset";

    //private IMap<String, Account> accountMap;

    public FraudRuleSet(Transaction txn) {
        //this.hazelcast = hz;
        this.transaction = txn;
        this.name = RULESET_ID;

        //accountMap = hz.getMap("accountMap");
        rules = new HashSet<>();
        rules.add(new LocationBasedRule(this));
        // TODO: more rules!

    }

    @Override
    public Transaction getItem() {
        return transaction;
    }

//    @Override
//    public AggregateOperation1<Transaction, ResultAccumulator, RuleSetEvaluationResult> getAggregator() {
//        return null;
//    }

    // TODO: fraud rule based on location
    class LocationBasedRule extends AbstractRule<Transaction,Integer> {

        LocationBasedRule(RuleSet set) {
            ruleSet = set;
        }

        @Override
        public boolean checkPreconditions(Transaction streamItem) {
            return true;
        }

        @Override
        public RuleEvaluationResult<Transaction,Integer> process(RuleTask<Transaction> task) {
            Transaction txn = task.getStreamItem();
            //Account acct = task.getAccountInfo();
//            IMap<String, Account> accountMap = jet.getHazelcastInstance().getMap("accountMap");
//            Account account = accountMap.get(acctNo);

            // get last reported location for account-linked device
            // return no-decision if time older than some threshhold
            // return true if distance of device within threshhold of swipe location
            // return false if distance of device greater threshhold

            // optionally return some value instead of boolean
            // 0 - no decision, (location expired or no location)
            // positive number - good correlation between locations
            // negative number - poor correlation between locations
            int score = 0;

            RuleEvaluationResult<Transaction,Integer> result =
                    new RuleEvaluationResult<>("Mobile device location check", RULESET_ID, txn);
            result.setEvaluationResult(score);
            return result;
            // TODO: calculate or set processing time?


        }

        public String toString() {
            return "LocationBasedRule";
        }
    }
    // TODO: fraud rule based on spending pattern
    // TODO: fraud rule based on vendor suspicion
}