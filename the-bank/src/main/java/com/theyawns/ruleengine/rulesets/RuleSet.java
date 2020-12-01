/*
 *  Copyright 2018-2021 Hazelcast, Inc
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.package com.theyawns.controller.launcher;
 */

package com.theyawns.ruleengine.rulesets;

import com.theyawns.ruleengine.HasID;
import com.theyawns.ruleengine.ItemCarrier;
import com.theyawns.ruleengine.rules.Rule;

import java.util.List;
import java.util.function.Function;

// T - type of item to apply rules to, e.g., Transaction
// R - type of final result from the ruleset, currently either Double (fraud rules)
//     or Boolean (credit rules)
public interface RuleSet<T extends HasID,R> extends Function<ItemCarrier<T>, RuleSetEvaluationResult<T,R>> {

    void add(Rule<T,R> rule);
    String getName();
    String getQualifiedName();  // includes category
    int getRuleCount();
    List<Rule<T,R>> getRules();
    // implementors can override this to filter out irrelevant input items
    default boolean isApplicableTo(T input) { return true; }
    default RuleSetSelectionFilter getSelectionFilter() { return new RuleSetSelectionFilter.AppliesToAll(); }

    // Now considering this a private implementation detail, not public API
    //RuleSetEvaluationResult<R> aggregateResults();
}
