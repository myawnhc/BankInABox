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

package com.theyawns.controller.launcher;

import com.hazelcast.config.EventJournalConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.theyawns.controller.config.EnvironmentSetup;

import java.util.Map;


public class ClusterMember {

    private final static ILogger log = Logger.getLogger(ClusterMember.class);
    private HazelcastInstance hz;

    public ClusterMember() {
        hz = Hazelcast.newHazelcastInstance();
    }

    public static void main(String[] args)
    {
    	new EnvironmentSetup();
        ClusterMember member = new ClusterMember();
        String clusterName = member.hz.getConfig().getClusterName();
        Map<Integer,String> factories = member.hz.getConfig().getSerializationConfig().getDataSerializableFactoryClasses();
        //log.info("IDSFactory 101:" + factories.get(101));
        EventJournalConfig ej = member.hz.getConfig().getMapConfig("preAuth").getEventJournalConfig();
        //log.info("ej " + ej);
        log.info("Member started in group " + clusterName);
    }
}