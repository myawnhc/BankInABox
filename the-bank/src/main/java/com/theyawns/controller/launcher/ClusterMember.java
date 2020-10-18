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
        log.info("IDSFactory 101:" + factories.get(101)); // VERIFIED
        EventJournalConfig ej = member.hz.getConfig().getMapConfig("preAuth").getEventJournalConfig();

        log.info("ej " + ej); // VERIFIED
        log.info("Member started in group " + clusterName); // VERIFIED
    }
}