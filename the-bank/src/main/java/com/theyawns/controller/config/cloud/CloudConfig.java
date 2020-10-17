package com.theyawns.controller.config.cloud;

import java.util.*;

public class CloudConfig {
    public String name;
    public String password;
    public String discoveryToken = null;
    public String urlBase = null;  // Only used in non-production environments (EA, UAT, etc.)
    public String keystorePassword = null; // also used for truststorePassword
    public String keystore = null;

    public CloudConfig(String name, String password, String discoveryToken) {
        this.name = name;
        this.password = password;
        this.discoveryToken = discoveryToken;
        this.urlBase = null;
    }

    public void setUrlBase(String urlBase) { this.urlBase = urlBase; }
    public Optional<String> getUrlBase() { return Optional.ofNullable(urlBase); }

    public void setKeystorePassword(String keystorePassword) {
        this.keystorePassword = keystorePassword;
    }
    public void setDiscoveryToken(String token) { this.discoveryToken = token; }
    public Optional<String> getDiscoveryToken() { return Optional.ofNullable(discoveryToken); }
    public Optional<String> getKeystorePassword() { return Optional.ofNullable(keystorePassword); }

    public void setKeystore(String path) { this.keystore = path; }
    public Optional<String> getKeystore() { return Optional.ofNullable(keystore); }

    public String toString() {
        return name + " " + password + " " + discoveryToken;
    }
}
