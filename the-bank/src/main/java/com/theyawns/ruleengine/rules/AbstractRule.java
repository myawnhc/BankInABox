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
