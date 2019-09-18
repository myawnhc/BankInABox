package com.theyawns.launcher;

public class KubernetesCheck {
	
	/**
	 * <p>Determine if we are in Kubernetes or not, and should
	 * use TCP discovery or the Kubernetes plugin discovery.
	 * </p>
	 * <p>The "<i>Dockerfile</i>" sets a value for "{@code hz.tcp.enabled}"
	 * so if the property isn't present, we can assume we are not
	 * running in Kubernetes.
	 * </p>
	 */
	KubernetesCheck() {
		System.setProperty("hz.tcp.enabled", 
				System.getProperty("hz.tcp.enabled", "true")
				);
	}

}
