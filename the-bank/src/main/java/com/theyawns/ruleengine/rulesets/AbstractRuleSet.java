package com.theyawns.ruleengine.rulesets;

import com.theyawns.ruleengine.HasID;
import com.theyawns.ruleengine.rules.Rule;
import com.theyawns.ruleengine.rules.RuleCategory;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

// T = ItemController<T>
public abstract class AbstractRuleSet<T extends HasID,R> implements RuleSet<T,R>, Serializable {

    protected String name;
    protected RuleCategory category;
    protected List<Rule<T,R>> rules = new ArrayList<>();

    @Override
    public void add(Rule<T,R> rule) { rules.add(rule); }

    public AbstractRuleSet(String name, RuleCategory category) {
        this.name = name;
        this.category = category;
        RuleSets.getInstance().addRuleSet(this, category);

    }

    public List<Rule<T,R>> getRules() { return rules; }

    public String getName() { return name; }
    public String getQualifiedName() { return category.name() + ":" + name; }
    public RuleCategory getCategory() { return category; }
    public int getRuleCount() { return rules.size(); }
}
