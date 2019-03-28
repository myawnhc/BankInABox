package com.theyawns.ruleengine;

import java.io.Serializable;
import java.util.Set;

public interface RuleSetFactory<T extends HasID> extends Serializable {
    Set<RuleSet<T>> getRuleSetsFor(T item);
}
