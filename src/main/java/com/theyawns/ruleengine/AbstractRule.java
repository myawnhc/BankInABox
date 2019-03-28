package com.theyawns.ruleengine;

public abstract class AbstractRule<T extends HasID,R> implements Rule<T,R> {

    protected RuleSet<T> ruleSet;
    protected T item;

    /** This default should be OK for any tasks that don't use an enriched stream.
     * Enriched objects will need a RuleTask subtype that handles use of the enrichment
     * @return
     */
    @Override
    public RuleTask<T> createTask() {
        return new RuleTask<T>(this, ruleSet.name, ruleSet.getItem());
    }

}
