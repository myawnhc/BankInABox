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

package com.theyawns.ruleengine.jetimpl.rulesets;

import com.theyawns.ruleengine.HasID;

import java.io.Serializable;

/** This class will represent the result of applying all the rules in a ruleset (or at least, all the rules whose
 *  preconditions are met).
 *
 *  When all rules are processed and aggregated, the aggregation will determine the overall result, which may have
 *  a different type than the individual RuleEvaluationResults.   For example, a recommendation engine ruleset might
 *  apply numeric scores to all the items under consideration, but the final RuleSet evaluation may be a simple
 *  boolean - recommend or do not recommend.
 */
@Deprecated
public class RuleSetEvaluationResult<T extends HasID, R> implements Serializable {

    R evaluationResult;
    T item;   // The stream item
    String itemKey;

    // NOT BEING SET; will probably deprecate and remove.  Because aggregation creates a Map.Entry for results,
    // the item key is available through the map's key and doesn't have to be part of the map value.
    public void setItem(T item) {
        this.item = item;
        itemKey = item.getItemID();
    }

    public void setEvaluationResult(R v) {
        evaluationResult = v;
    }

    public R getEvaluationResult() {
        return evaluationResult;
    }

    public String getItemKey() {
        return itemKey;
    }

    public String toString() {
        return "RSER for item " + itemKey + ": " + evaluationResult;
    }
}
