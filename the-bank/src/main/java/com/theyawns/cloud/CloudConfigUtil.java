package com.theyawns.cloud;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.impl.spi.impl.discovery.HazelcastCloudDiscovery;
import com.hazelcast.client.properties.ClientProperty;

import java.net.URL;
import java.util.Map;

public class CloudConfigUtil {
    public static final String NAME_KEY            = "name";
    public static final String PASSWORD_KEY        = "password";
    public static final String DISCOVERY_TOKEN_KEY = "discovery-token";
    public static final String URL_BASE_KEY        = "url-base";
    public static final String KEYSTORE_PASS_KEY   = "keystore-password";
    public static final String KEYSTORE_FILE_KEY   = "keystore-file";

    private static Map<String, Map> configs;
    private static String defaultConfigName;

    static class ConfigInfo {
        String defaultConfig;
        Map<String, Map> clusterConfiguration;
        public ConfigInfo() {}
        public void setDefaultConfig(String config) { this.defaultConfig = config; }
        public void setClusterConfiguration(Map<String, Map> configs) {
            this.clusterConfiguration = configs;
        }
    }

    static {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        //Map<String, Map<String, Map>> properties = null;
        ConfigInfo configInfo;
        try {
            URL yamlFile = CloudConfigUtil.class.getClassLoader().getResource("bib-client-config.yaml");
            System.out.println("Reading cloud config info from " + yamlFile.toExternalForm());
            configInfo = mapper.readValue(yamlFile, ConfigInfo.class);
            defaultConfigName = configInfo.defaultConfig;
            configs = configInfo.clusterConfiguration;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Was private; now public so launcher can override some values for K8S deployment
    public static CloudConfig getConfig(String configname) {
        Map<String,String> configProperties = configs.get(configname);
        CloudConfig cc = new CloudConfig(configProperties.get(NAME_KEY),
                                         configProperties.get(PASSWORD_KEY),
                                         configProperties.get(DISCOVERY_TOKEN_KEY));
        cc.setUrlBase(configProperties.get(URL_BASE_KEY));
        cc.setKeystorePassword(configProperties.get(KEYSTORE_PASS_KEY));
        cc.setKeystore(configProperties.get(KEYSTORE_FILE_KEY));
        return cc;
    }

//    public static String getDefaultConfigName() { return defaultConfigName; }
//    public static ClientConfig getDefaultConfig() {
//        return getClientConfigForCluster(defaultConfigName);
//    }

    public static ClientConfig getClientConfigForCluster(String platform) {

        CloudConfig cloudConfig = getConfig(platform);

        ClientConfig config = new ClientConfig();
        config.setClusterName(cloudConfig.name);
        //config.setProperty("hazelcast.client.statistics.enabled", "true");
        if (cloudConfig.getDiscoveryToken().isPresent())
            config.setProperty(ClientProperty.HAZELCAST_CLOUD_DISCOVERY_TOKEN.getName(), cloudConfig.getDiscoveryToken().get());
        if (cloudConfig.getUrlBase().isPresent())
            config.setProperty(HazelcastCloudDiscovery.CLOUD_URL_BASE_PROPERTY.getName(), cloudConfig.getUrlBase().get());
        // TODO: keystore password
        // TODO: keystore file / resource
        //System.out.println(config.toString());
        return config;
    }



    public static void main(String[] args) {
        System.out.println("Default config is " + defaultConfigName);
    }
}
