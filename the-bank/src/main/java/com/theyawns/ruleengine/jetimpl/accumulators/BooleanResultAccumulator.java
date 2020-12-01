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

package com.theyawns.ruleengine.jetimpl.accumulators;

import com.theyawns.ruleengine.HasID;
import com.theyawns.ruleengine.jetimpl.rules.RuleEvaluationResult;
import com.theyawns.ruleengine.jetimpl.rulesets.RuleSetEvaluationResult;

/**
 * Jet accumulator to work with ResultAccumulator instances whose parameterized result type is Boolean.
 * <p>
 * This class is not used in the main flow of the banking demo, but is still used by some side projects
 * that run parallel rules on the IMDG and Jet clusters independently.
 *
 * @param <T> the type of items in the stream.  In all current usages this is Transaction, but the
 *            rule engine related classes in this demo were intended to be reused in other domains where
 *            the stream type could vary.
 * @see com.theyawns.ruleengine.jetimpl.accumulators.ResultAccumulator
 */
public class BooleanResultAccumulator<T extends HasID> extends ResultAccumulator<T, Boolean> {

    // This method is only for use when the parameterized type of Rules is Boolean ... is there a better abstraction?
    public RuleSetEvaluationResult<T, Boolean> anyTrue() {
        // We need to actually process the evaluation rule collection and set the RSER state accordingly
        RuleSetEvaluationResult<T, Boolean> ruleSetEvaluationResult = new RuleSetEvaluationResult<>();

        ruleSetEvaluationResult.setEvaluationResult(false);
        for (RuleEvaluationResult<T, Boolean> rer : allResults) {

            if (rer.getItemId() == null) {
                System.out.println("!!!!! NULL item id in ResultAccumulator.anyTrue() accumulator");

            } else {

                ruleSetEvaluationResult.setItem(rer.getItem());

                if ((Boolean) rer.getEvaluationResult()) {
                    ruleSetEvaluationResult.setEvaluationResult(true);
                    return ruleSetEvaluationResult;
                }
            }

        }
        return ruleSetEvaluationResult;
    }

    public RuleSetEvaluationResult<T, Boolean> allTrue() {
        // We need to actually process the evaluation rule collection and set the RSER state accordingly
        RuleSetEvaluationResult<T, Boolean> ruleSetEvaluationResult = new RuleSetEvaluationResult<>();

        ruleSetEvaluationResult.setEvaluationResult(true);
        for (RuleEvaluationResult<T, Boolean> rer : allResults) {

            if (rer.getItemId() == null) {
                System.out.println("!!!!! NULL item id in ResultAccumulator.allTrue() accumulator");

            } else {

                ruleSetEvaluationResult.setItem(rer.getItem());

                if (!rer.getEvaluationResult()) {
                    ruleSetEvaluationResult.setEvaluationResult(false);
                    return ruleSetEvaluationResult;
                }
            }

        }
        return ruleSetEvaluationResult;
    }
}
