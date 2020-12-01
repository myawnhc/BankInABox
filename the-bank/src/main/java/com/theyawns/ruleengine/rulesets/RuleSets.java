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

// Collection of RuleSets.  RuleSet constructors are responsible for registering themselves here

import com.theyawns.ruleengine.rules.RuleCategory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RuleSets {

    private static RuleSets instance;

    private static Map<RuleCategory, List<RuleSet>> allRuleSets = new HashMap<>();
    //private static Map<String, Integer> pnIndexMap = new HashMap<>();

    private static int pnCounter = 0;


    private RuleSets() {
    }

    // TODO: this isn't threadsafe.
    public static RuleSets getInstance() {
        if (instance == null) {
            instance = new RuleSets();
        }
        System.out.println("RuleSets instance " + instance);
        return instance;
    }

    public void addRuleSet(RuleSet set, RuleCategory category) {
        System.out.println("addRuleSet on instance " + instance);

        List<RuleSet> setsForCategory = allRuleSets.get(category);
        if (setsForCategory == null) {
            setsForCategory = new ArrayList<>();
        }
        setsForCategory.add(set);
        allRuleSets.put(category, setsForCategory);
        //pnIndexMap.put(set.getQualifiedName(), pnCounter++);
        //System.out.println("Assigned " + set.getQualifiedName() + " PN Index " + (pnCounter-1));
    }

//    public int getPNIndex(String fqRuleName) {
//        System.out.println("getPNInstance on RuleSets instance " + instance);
//        System.out.println("getPNInstance instance " + instance + " has map " + pnIndexMap);
//        System.out.println("   size " + pnIndexMap.size());
//        Integer index = pnIndexMap.get(fqRuleName);
//        return index == null ? -1 : index;
//    }

    public List<RuleSet> getAllForCategory(RuleCategory category) {
        return allRuleSets.get(category);
    }

    public List<RuleSet> getAll() {
        List<RuleSet> all = new ArrayList<RuleSet>();
        for (RuleCategory c : allRuleSets.keySet()) {
            all.addAll(allRuleSets.get(c));
        }
        return all;
    }
}
