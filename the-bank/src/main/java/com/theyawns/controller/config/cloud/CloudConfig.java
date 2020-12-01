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
