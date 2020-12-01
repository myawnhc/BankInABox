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

package com.theyawns.ruleengine.holding;

import com.theyawns.ruleengine.rules.RuleExecutionPlatform;

import java.io.Serializable;

@Deprecated
public abstract class AbstractEvaluationResult<R> implements Serializable {

    public long startTime;
    public long endTime;
    public long evaluationTime;

    // Accumulated result of rule evaluations
    // Fraud Rules will have double result type; percentage likelihood that txn is fraudulent
    // Payment rules will have a Boolean result type - true = pay, false = reject
    private R result;

    private RuleExecutionPlatform platform;

    protected void recordStart() {
        startTime = System.nanoTime();
    }

    protected void recordStop() {
        endTime = System.nanoTime();
    }

    protected long getElapsedNanos() {
        return endTime - startTime;
    }

    protected long getElapsedMillis() {
        return (endTime - startTime) / 1000;
    }

    protected R getResult() {
        return result;
    }
}
