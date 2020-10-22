package com.theyawns.ruleengine.rulesets;

import java.io.Serializable;
import java.util.function.Function;

public interface RuleSetSelectionFilter<T> extends Function<T, Boolean>  {
    public class AppliesToAll<T> implements RuleSetSelectionFilter<T>, Serializable {
        public Boolean apply(T item) { return true; }
    }
}
