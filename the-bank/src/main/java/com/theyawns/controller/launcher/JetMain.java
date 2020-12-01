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

import com.hazelcast.config.Config;
import com.hazelcast.config.NetworkConfig;
import com.hazelcast.config.XmlConfigBuilder;
import com.hazelcast.jet.Jet;
import com.hazelcast.jet.JetInstance;
import com.hazelcast.jet.config.JetConfig;
import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.theyawns.controller.config.EnvironmentSetup;

public class JetMain {
    private static final ILogger log = Logger.getLogger(JetMain.class);
    		
    private JetConfig jetConfig;
	private JetInstance  jetInstance;

    protected void init() {
        XmlConfigBuilder xccb = new XmlConfigBuilder(); // Reads hazelcast.xml
        Config hazelcastConfig = xccb.build();
        NetworkConfig networkConfig = hazelcastConfig.getNetworkConfig();
        networkConfig.setPort(5710); // Avoid collision between internal and external IMDG clusters
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
