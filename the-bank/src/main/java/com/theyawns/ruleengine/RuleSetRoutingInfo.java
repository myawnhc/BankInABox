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

package com.theyawns.ruleengine;

import com.hazelcast.collection.IQueue;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.theyawns.ruleengine.rulesets.RuleSet;
import com.theyawns.ruleengine.rulesets.RuleSetSelectionFilter;

import java.io.Serializable;

// Info related to the deployment of a ruleset within the RuleEngine
// Will be stored in IMap RuleSetRoutingMap
public class RuleSetRoutingInfo<T> implements Serializable, HazelcastInstanceAware  {

    private enum ChannelType {IQueue} // more to come

    private transient HazelcastInstance hazelcast;

    //private RuleSet ruleSet;
    private RuleSetSelectionFilter<T> filter;
    private boolean enabled;
    private ChannelType channel;
    // possibly version info?
    // possibly reference to a Jet job where the RuleSet is running

    // We will expand the number of routing options as needed - it may
    // be that we support IQueue, Topic, perhaps Kafka or JMS, etc.
    // As we do so, the addRuleSet method of the outer class will be
    // overloaded to take additional communication channels
    private String queueName;
    private transient IQueue inputQueue;

    public RuleSetRoutingInfo(RuleSet set, String queueName) {
        this.filter = set.getSelectionFilter();
        this.queueName = queueName;
        this.channel = ChannelType.IQueue;
    }

    public boolean isApplicableTo(T item) {
        return filter.apply(item);
    }

    // channel & queue are internal for IMDG, but name exposed for Jet
    public String getQueueName() { return queueName; }

//    public RuleSet getRuleSet() { return ruleSet; }
//    public void setRuleSet(RuleSet set) { ruleSet = set; }

    public void routeItem(T item) {
        switch (channel) {
            case IQueue:
                try {
                    inputQueue.put(item);
                    //System.out.println("Put " + item + " on " + inputQueue.getName());
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
        }
    }

    @Override
    public void setHazelcastInstance(HazelcastInstance hazelcastInstance) {
        this.hazelcast = hazelcastInstance;
        this.inputQueue = hazelcast.getQueue(queueName);
    }
}
