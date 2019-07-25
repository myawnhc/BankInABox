package com.theyawns.domain.payments;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.crdt.pncounter.PNCounter;
import com.hazelcast.scheduledexecutor.NamedTask;
import com.theyawns.sink.Graphite;

import java.io.IOException;
import java.io.Serializable;

/** Runnable task that pumps stats from several IMaps to Graphite / Grafana */
public class PumpGrafanaStats implements Serializable, Runnable, HazelcastInstanceAware, NamedTask {

    private transient HazelcastInstance hazelcast;
    private transient PNCounter approvalCounter;
    private transient PNCounter rejectedForMerchantTxnAvg;
    private transient PNCounter rejectedForCreditLimitRule;
    private transient PNCounter merchant1_10;
    private transient PNCounter merchant11_20;

    private transient Graphite graphite;
    private boolean initialized = false;

    private static int previouslyReportedApprovals = 0;

    private void init() {
        approvalCounter = hazelcast.getPNCounter("approvalCounter");
        rejectedForMerchantTxnAvg = hazelcast.getPNCounter("rejectedFraudAvgTxnAmt");
        rejectedForCreditLimitRule = hazelcast.getPNCounter("rejectedPaymentCreditLimit");

        merchant1_10 = hazelcast.getPNCounter("merchant1_10");
        merchant11_20 = hazelcast.getPNCounter( "merchant11_20");
        graphite = new Graphite();
        initialized = true;
    }

    // Runs at intervals
    @Override
    public void run() {
        System.out.println("PumpGrafanaStats active");
        if (!initialized)
            init();

        // counters are integers but we want to do floating-point math with the results
        double approved = (double) approvalCounter.get();
        double rejectedFraud0 = (double) rejectedForMerchantTxnAvg.get();
        double rejectedCredit0 = (double) rejectedForCreditLimitRule.get();
        double total = approved + rejectedCredit0 + rejectedFraud0;
        double fraudRate = 0.0;
        if (total > 0)
            fraudRate = rejectedFraud0 / total;
        try {
            // TODO: write bib.payments.amazon, bib.payments.walmart from some collection
            // of merchants (maybe 1-10 Amazon, 11-20 Walmart ?)
            graphite.writeStats("bib.fraud.rate", fraudRate);
            System.out.printf("  Fraud rate = %f + %f + %f = %f / %f = %f\n",
                    approved, rejectedCredit0, rejectedFraud0, total, rejectedFraud0, fraudRate);
            graphite.writeStats("bib.payment.rate", approved - previouslyReportedApprovals);
            previouslyReportedApprovals = (int) approved;
            graphite.writeStats("bib.payments.amazon", merchant1_10.get());
            graphite.writeStats("bib.payments.walmart", merchant11_20.get());
            System.out.printf("  Payments by merchant %d %d\n", merchant1_10.get(), merchant11_20.get() );
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println("PumpGrafanaStats complete");
    }

    @Override
    public void setHazelcastInstance(HazelcastInstance hazelcastInstance) {
        this.hazelcast = hazelcastInstance;
        init();
    }

    @Override
    public String getName() {
        return "PumpGrafanaStats";
    }
}
