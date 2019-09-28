package com.theyawns.launcher;

import com.hazelcast.config.Config;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.config.XmlConfigBuilder;
import com.hazelcast.jet.Jet;
import com.hazelcast.jet.JetInstance;
import com.hazelcast.jet.config.JetConfig;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.theyawns.util.EnvironmentSetup;

public class JetMain {
    private static final ILogger log = Logger.getLogger(JetMain.class);
    		
    private JetConfig jetConfig;
	private JetInstance  jetInstance;

    protected void init() {
        XmlConfigBuilder xccb = new XmlConfigBuilder(); // Reads hazelcast.xml
        Config hazelcastConfig = xccb.build();
        NetworkConfig networkConfig = hazelcastConfig.getNetworkConfig();
        //TODO Use 5710 in Kubernetes also
        if (!System.getProperty("hz.kubernetes.enabled").equalsIgnoreCase("true")) {
            networkConfig.setPort(5710); // Avoid collision between internal and external IMDG clusters
        }
        hazelcastConfig.getCPSubsystemConfig().setCPMemberCount(0); // no CP needed on internal cluster
        jetConfig = new JetConfig();
        jetConfig.setHazelcastConfig(hazelcastConfig);
    }

    public void run() {
        init();
        this.jetInstance = Jet.newJetInstance(jetConfig);
    	log.info(String.format("Member '%s' started", this.jetInstance.getName()));
    }
    
    public static void main(String[] args)
    {
    	new EnvironmentSetup();
    	JetMain jetMain = new JetMain();
    	jetMain.run();
    }
}
