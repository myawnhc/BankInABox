package com.theyawns.launcher;

import java.util.concurrent.TimeUnit;

import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;

public class KubernetesCheck {
	
    private final static ILogger log = Logger.getLogger(KubernetesCheck.class);

    private static final String[] PROPERTIES = {
    		"hz.kubernetes.enabled",
    		"hz.management.center",
    		"hz.service.dns",
    		"hz.tcp.enabled",
    };
    
    // Reference array, so all are logged
    private static final String KUBERNETES_ENABLED = PROPERTIES[0];
    private static final String MANAGEMENT_CENTER = PROPERTIES[1];
    private static final String SERVICE_NAME = PROPERTIES[2];
    private static final String TCP_ENABLED = PROPERTIES[3];

    private static final String MANAGEMENT_CENTER_SERVICE
    	= "bankinabox-hazelcast-management-center";
    private static final String NAMESPACE = "default.svc.cluster.local";
    private static final String SERVER_SERVICE
    	= "bankinabox-hazelcast-server" + "." + NAMESPACE;
    
	/**
	 * <p>Determine if we are in Kubernetes or not, and should
	 * use TCP discovery or the Kubernetes plugin discovery.
	 * </p>
	 * <p>The "<i>Dockerfile</i>" sets a value for "{@code hz.tcp.enabled}"
	 * so if the property isn't present, we can assume we are not
	 * running in Kubernetes. Same for "@{code hz.kubernetyes.enabled}".
	 * </p>
	 */
	KubernetesCheck() {
		System.setProperty(TCP_ENABLED, 
				System.getProperty(TCP_ENABLED, "true")
				);
		System.setProperty(KUBERNETES_ENABLED, 
				System.getProperty(KUBERNETES_ENABLED, "false")
				);
		
		if (System.getProperty(KUBERNETES_ENABLED).equalsIgnoreCase("true")) {
			System.setProperty(MANAGEMENT_CENTER,
					MANAGEMENT_CENTER_SERVICE);
			System.setProperty(SERVICE_NAME,
					SERVER_SERVICE);
			
			
			if (System.getProperty(TCP_ENABLED).equalsIgnoreCase("true")) {
				log.severe("TCP and Kubernetes discovery are both enabled.");
			} else {
				if (log.isInfoEnabled()) {
					log.info("Kubernetes discovery is enabled");
				}
			}
		} else {
			System.setProperty(MANAGEMENT_CENTER,
					"localhost");
			System.setProperty(SERVICE_NAME,
					"");
			
			if (System.getProperty(TCP_ENABLED).equalsIgnoreCase("true")) {
				if (log.isInfoEnabled()) {
					log.info("TCP discovery is enabled");
				}
			} else {
				if (log.isWarningEnabled()) {
					log.warning("Neither TCP not Kubernetes discovery is enabled");
				}
			}
		}
		
		if (log.isInfoEnabled()) {
			for (String property : PROPERTIES) {
				log.info("Set '" + property + "'=='" + System.getProperty(property) + "'");
			}
		}
		try {
			TimeUnit.SECONDS.sleep(15);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
