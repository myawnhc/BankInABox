package com.theyawns.launcher;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;


public class ClusterMember {

    private final static ILogger log = Logger.getLogger(ClusterMember.class);
    private HazelcastInstance hz;

    public ClusterMember() {
        hz = Hazelcast.newHazelcastInstance();
    }

    public static void main(String[] args)
    {
        ClusterMember member = new ClusterMember();
        String groupname = member.hz.getConfig().getGroupConfig().getName();
        log.info("Member started in group " + groupname);
    }
}