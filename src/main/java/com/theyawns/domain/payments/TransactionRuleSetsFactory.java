package com.theyawns.domain.payments;

import com.theyawns.ruleengine.RuleSet;
import com.theyawns.ruleengine.RuleSetFactory;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

@Deprecated
public class TransactionRuleSetsFactory implements RuleSetFactory<Transaction>, Serializable {


    public Set<RuleSet<Transaction>> getRuleSetsFor(Transaction item) {
        FraudRuleSet frs = new FraudRuleSet(item);
        PaymentsRuleSet prs = new PaymentsRuleSet(item);
        Set<RuleSet<Transaction>> setOfRuleSets = new HashSet<>();
        //setOfRuleSets.add(frs);     TODO: re-enable this when FRS has some rules; also look at merging pipeline back
        setOfRuleSets.add(prs);
        return setOfRuleSets;
    }
}
