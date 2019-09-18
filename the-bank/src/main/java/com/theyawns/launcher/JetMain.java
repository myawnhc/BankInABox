package com.theyawns.launcher;

import com.hazelcast.config.Config;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.config.XmlConfigBuilder;
import com.hazelcast.jet.Jet;
import com.hazelcast.jet.JetInstance;
import com.hazelcast.jet.Job;
import com.hazelcast.jet.config.JetConfig;
import com.hazelcast.jet.pipeline.Pipeline;

public class JetMain {

    private JetConfig jetConfig;

    protected void init() {
        XmlConfigBuilder xccb = new XmlConfigBuilder(); // Reads hazelcast.xml
        Config hazelcastConfig = xccb.build();
        NetworkConfig networkConfig = hazelcastConfig.getNetworkConfig();
        networkConfig.setPort(5710); // Avoid collision between internal and external IMDG clusters
        hazelcastConfig.getCPSubsystemConfig().setCPMemberCount(0); // no CP needed on internal cluster
        hazelcastConfig.getGroupConfig().setName("jet-dev"); // try not to confuse mancenter

        jetConfig = new JetConfig();
        jetConfig.setHazelcastConfig(hazelcastConfig);
    }

    public void run() {
        init();
        JetInstance jet = Jet.newJetInstance(jetConfig);
    }
}
