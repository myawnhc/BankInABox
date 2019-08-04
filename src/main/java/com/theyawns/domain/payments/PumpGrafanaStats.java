package com.theyawns.domain.payments;

import com.hazelcast.core.DistributedObject;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.crdt.pncounter.PNCounter;
import com.hazelcast.scheduledexecutor.NamedTask;
import com.theyawns.Constants;
import com.theyawns.sink.Graphite;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/** Runnable task that pumps stats from several IMaps to Graphite / Grafana */
public class PumpGrafanaStats implements Serializable, Runnable, HazelcastInstanceAware, NamedTask {

    private transient HazelcastInstance hazelcast;
    private transient PNCounter approvalCounter;
    private transient PNCounter rejectedForFraud;
    private transient PNCounter rejectedForCredit;
    // Suspect these get dropped when we start showing reject rates by rule
    private transient PNCounter walmart;
    private transient PNCounter amazon;

    private transient Map<String, PNCounter> rejectionByRule;

    private transient Graphite graphite;
    private boolean initialized = false;

    private static int previouslyReportedApprovals = 0;

    private void init() {

        approvalCounter = hazelcast.getPNCounter(Constants.PN_COUNT_APPROVED);
        rejectedForCredit = hazelcast.getPNCounter(Constants.PN_COUNT_REJ_CREDIT);
        rejectedForFraud = hazelcast.getPNCounter(Constants.PN_COUNT_REJ_FRAUD);
        walmart = hazelcast.getPNCounter(Constants.PN_COUNT_WALMART);
        amazon = hazelcast.getPNCounter(Constants.PN_COUNT_AMAZON);
        graphite = new Graphite();
        rejectionByRule = new HashMap<>();
        initialized = true;
    }

    // This needs to run after processing has started so that PNCounters will have been
    // created.  If rulesets are dynamically changing, we may need to periodically
    // re-run this.
    private void findCounters() {
        Collection<DistributedObject> allObjects = hazelcast.getDistributedObjects();
        for (DistributedObject dobj : allObjects) {
            if (dobj instanceof PNCounter) {
                PNCounter pnc = (PNCounter) dobj;
                if (pnc.getName().startsWith("pnCounter"))
                    continue;   // Will be handled individually
                // What remains are the ruleset-specific rejections
                rejectionByRule.put(dobj.getName(), pnc);
                System.out.println("Found rule-specific counter " + dobj.getName());
            }
        }
    }

    // Runs at intervals
    @Override
    public void run() {
        //System.out.println("PumpGrafanaStats active");
        if (!initialized)
            init();

        findCounters();  // TODO: run this at a less frequent interval

        // counters are integers but we want to do floating-point math with the results
        double approved = (double) approvalCounter.get();
        double rejectedFraud = (double) rejectedForFraud.get();
        double rejectedCredit = (double) rejectedForCredit.get();
        double total = approved + rejectedFraud + rejectedCredit;
        double fraudRate = 0.0;
        if (total > 0)
            fraudRate = rejectedFraud / total;
        try {

            graphite.writeStats("bib.fraud.rate", fraudRate);
            System.out.printf("  Fraud rate = %f + %f + %f = %f / %f = %f\n",
                    approved, rejectedCredit, rejectedFraud, total, rejectedFraud, fraudRate);
            graphite.writeStats("bib.payment.rate", approved - previouslyReportedApprovals);
            previouslyReportedApprovals = (int) approved;
            graphite.writeStats("bib.payments.amazon", amazon.get());
            graphite.writeStats("bib.payments.walmart", walmart.get());
            //System.out.printf("  Payments by merchant %d %d\n", merchant1_10.get(), merchant11_20.get() );
            if (rejectionByRule != null && rejectionByRule.size() > 0) {
                for (PNCounter counter : rejectionByRule.values()) {
                    graphite.writeStats("bib.rejectedby." + counter.getName(), counter.get());
                }
            } else {
                System.out.println("Skipping rule specific counters because " + rejectionByRule == null ? "null" : "empty");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
        //System.out.println("PumpGrafanaStats complete");
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
