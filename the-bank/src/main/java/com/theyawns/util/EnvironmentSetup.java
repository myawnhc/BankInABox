package com.theyawns.util;

import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;

import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * <p>This class sets/overrides Java system properties that may
 * be in Hazelcast config files, so should be called before these
 * Hazelcast config files are used.
 * </p>
 */
public class EnvironmentSetup {
	
    private static final ILogger log = Logger.getLogger(EnvironmentSetup.class);

    private static final String[] PROPERTIES = {
    		"hz.grafana",
    		"hz.groupName",
    		"hz.is.imdg",
    		"hz.kubernetes.enabled",
    		"hz.management.center",
    		"hz.maria",
    		"hz.service.dns",
    		"hz.service.port",
    		"hz.tcp.enabled",
    };
    
    // Reference array, so all are logged
    public  static final String GRAFANA = PROPERTIES[0];
    private static final String GROUP_NAME = PROPERTIES[1];
    private static final String IS_IMDG = PROPERTIES[2];
    public  static final String KUBERNETES_ENABLED = PROPERTIES[3];
    private static final String MANAGEMENT_CENTER = PROPERTIES[4];
    private static final String MARIA = PROPERTIES[5];
    private static final String SERVICE_NAME = PROPERTIES[6];
    private static final String SERVICE_PORT = PROPERTIES[7];
    private static final String TCP_ENABLED = PROPERTIES[8];

    private static final String NAMESPACE = "default.svc.cluster.local";
    private static final String GRAFANA_SERVICE
		= "bankinabox-grafana-service" + "." + NAMESPACE;
    private static final String MANAGEMENT_CENTER_SERVICE
    	= "bankinabox-management-center-service" + "." + NAMESPACE;
    public  static final String IMDG_PORT = "5701";
    public  static final String IMDG_SERVICE
    	= "bankinabox-imdg-service" + "." + NAMESPACE;
    public  static final String JET_PORT = "5710";
    public  static final String JET_SERVICE
    	= "bankinabox-jet-service" + "." + NAMESPACE;
    public  static final String MARIA_SERVICE
		= "bankinabox-maria-service" + "." + NAMESPACE;
    
	/**
	 * <p>Determine if we are in Kubernetes or not, and should
	 * use TCP discovery or the Kubernetes plugin discovery.
	 * </p>
	 * <p>The "<i>Dockerfile</i>" sets a value for "{@code hz.tcp.enabled}"
	 * so if the property isn't present, we can assume we are not
	 * running in Kubernetes. Same for "@{code hz.kubernetyes.enabled}".
	 * </p>
	 */
	public EnvironmentSetup() {
		if (false && log.isInfoEnabled()) {
			System.getProperties().keySet()
			.stream()
			.filter(key -> key.toString().toLowerCase().startsWith("hz"))
			.sorted()
			.forEach(key -> log.info("'" + key + "'=='" + System.getProperty(key.toString()) + "'"));
		}

		System.setProperty(IS_IMDG, 
				System.getProperty(IS_IMDG, "true")
				);
		System.setProperty(KUBERNETES_ENABLED, 
				System.getProperty(KUBERNETES_ENABLED, "false")
				);
		System.setProperty(TCP_ENABLED, 
				System.getProperty(TCP_ENABLED, "true")
				);

		if (System.getProperty(IS_IMDG).equalsIgnoreCase("true")) {
			System.setProperty(GROUP_NAME, "BankInABox");
		} else {
			System.setProperty(GROUP_NAME, "JetInABox");
		}

		if (System.getProperty(KUBERNETES_ENABLED).equalsIgnoreCase("true")) {
			System.setProperty(GRAFANA,
					GRAFANA_SERVICE);
			System.setProperty(MANAGEMENT_CENTER,
					MANAGEMENT_CENTER_SERVICE);
			System.setProperty(MARIA, MARIA_SERVICE);
			if (System.getProperty(IS_IMDG).equalsIgnoreCase("true")) {
				System.setProperty(SERVICE_PORT, IMDG_PORT);
				System.setProperty(SERVICE_NAME, IMDG_SERVICE);				
			} else {
				System.setProperty(SERVICE_PORT, JET_PORT);
				System.setProperty(SERVICE_NAME, JET_SERVICE);				
			}
			
			if (System.getProperty(TCP_ENABLED).equalsIgnoreCase("true")) {
				log.severe("TCP and Kubernetes discovery are both enabled.");
			} else {
				if (log.isInfoEnabled()) {
					log.info("Kubernetes discovery is enabled");
				}
			}
		} else {
			System.setProperty(GRAFANA,
					"localhost");
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

		if (false && log.isInfoEnabled()) {
			log.info("*****************************");
			log.info("<< MANIFEST.MF >>");
			try {
				Enumeration<URL> enumeration
					= EnvironmentSetup.class.getClassLoader().getResources("META-INF/MANIFEST.MF");
				if (!enumeration.hasMoreElements()) {
					log.severe("No enumeration for manifest");
				} else {
					URL url = enumeration.nextElement();
					try (InputStream inputStream = url.openStream()) {
						Manifest manifest = new Manifest(inputStream);
						Attributes attributes = manifest.getMainAttributes();
						attributes.entrySet().stream().forEach(e -> log.info(e.toString()));
					}
				}
			} catch (Exception e) {
				log.severe(e.getMessage());
			}
			log.info("<< PROPERTIES >>");
			for (String property : PROPERTIES) {
				log.info("Set '" + property + "'=='" + System.getProperty(property) + "'");
			}
			log.info("*****************************");
		}
	}
}
