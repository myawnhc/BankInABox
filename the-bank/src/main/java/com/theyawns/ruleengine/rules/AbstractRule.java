package com.theyawns.ruleengine.rules;

import com.theyawns.ruleengine.rulesets.RuleSet;

import java.io.Serializable;

public abstract class AbstractRule<T,R> implements Rule<T,R>, Serializable {

    protected RuleSet parent;
    protected String  name;
    protected RuleCategory category;
    protected boolean enabled = true;

    // Fields used to evaluate rule
    protected double rejectRate;
    protected double correlation;  // not used initially; track variance against other rules

    // Benchmarking data - not used in Demo mode
    protected int tps;
    protected long startTime;
    protected long endTime;

    public AbstractRule(String name, RuleSet parent, RuleCategory category) {
        this.name = name;
        this.parent = parent;
        this.category = category;
    }

    public String getName() { return name; }
    public String getQualifiedName() {
        return category.name() + ":" + parent.getName() + name;
    }

}
