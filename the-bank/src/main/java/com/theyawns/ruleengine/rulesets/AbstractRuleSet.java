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
import com.theyawns.ruleengine.rules.Rule;
import com.theyawns.ruleengine.rules.RuleCategory;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

// T = ItemController<T>
public abstract class AbstractRuleSet<T extends HasID,R> implements RuleSet<T,R>, Serializable {

    protected String name;
    protected RuleCategory category;
    protected List<Rule<T,R>> rules = new ArrayList<>();

    @Override
    public void add(Rule<T,R> rule) { rules.add(rule); }

    public AbstractRuleSet(String name, RuleCategory category) {
        this.name = name;
        this.category = category;
        RuleSets.getInstance().addRuleSet(this, category);

    }

    public List<Rule<T,R>> getRules() { return rules; }

    public String getName() { return name; }
    public String getQualifiedName() { return category.name() + ":" + name; }
    public RuleCategory getCategory() { return category; }
    public int getRuleCount() { return rules.size(); }
}
