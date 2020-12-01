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

package com.theyawns.banking.fraud.fdengine.jetimpl.function;

import com.theyawns.ruleengine.HasID;
import com.theyawns.ruleengine.jetimpl.rules.RuleEvaluationResult;

import java.io.Serializable;

/** This could just be collapsed with ToRuleEvaluationResultFunction, but keeping the distinction to follow
 *  the model established by Jet functional interfaces that extend the Java function library
 *
 *  @param <T>
 */
@FunctionalInterface
public interface DistributedToRuleEvaluationResultFunction<T extends HasID,R> extends ToRuleEvaluationResultFunction<T,R>, Serializable {

    RuleEvaluationResult<T,R> applyAsResultEx(RuleEvaluationResult<T,R> value) throws Exception;

    @Override
    default RuleEvaluationResult<T,R> applyAsResult(RuleEvaluationResult<T, R> value) {
        try {
            return applyAsResultEx(value);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);  // convert to unchecked
        }
    }
}