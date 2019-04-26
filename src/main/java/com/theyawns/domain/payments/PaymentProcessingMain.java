package com.theyawns.domain.payments;

import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.config.Config;
import com.hazelcast.config.ManagementCenterConfig;
import com.hazelcast.jet.Jet;
import com.hazelcast.jet.JetInstance;
import com.hazelcast.jet.config.JetConfig;

@Deprecated
public class PaymentProcessingMain {

    public static final String IMDG_HOST = "localhost:5701";


    public static void main(String[] args) throws InterruptedException {

        ManagementCenterConfig mcc = new ManagementCenterConfig();
        mcc.setEnabled(true);
        mcc.setUrl("http://localhost:8080/hazelcast-mancenter");

        ClientConfig hzConfig = new ClientConfig();
        hzConfig.getGroupConfig().setName("dev").setPassword("ignored");
        hzConfig.getNetworkConfig().addAddress(IMDG_HOST);

        JetConfig jc = new JetConfig();
        Config hazelcastConfig = jc.getHazelcastConfig();
        // Avoid collision between the external IMDG (remoteMap) and the internal IMDG
        hazelcastConfig.getNetworkConfig().setPort(5710); // Group name defaults to Jet but port still defaults to 5701
        hazelcastConfig.setManagementCenterConfig(mcc);

        jc.setHazelcastConfig(hazelcastConfig);


        System.out.println("Starting Jet instance"); // TODO: should defer this until we're ready to run the job!
        JetInstance jet = Jet.newJetInstance(jc);

        // Simple pipeline here:
        // Read the MapJournal of the preAuth map from IMDG
        // Enrich with RuleList from the ReplicatedMap RuleEnablement
        // Drain to

        // Factory handles both the Payments and Fraud rulesets
        //RuleSetFactory rsFactory = new TransactionRuleSetsFactory();=
        //JetRuleEngine<Transaction> engine = new JetRuleEngine<Transaction>(jet, hzConfig, rsFactory);

    }

}
