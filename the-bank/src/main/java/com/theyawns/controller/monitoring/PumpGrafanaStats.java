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

package com.theyawns.controller.monitoring;

import com.hazelcast.core.DistributedObject;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceAware;
import com.hazelcast.crdt.pncounter.PNCounter;
import com.hazelcast.scheduledexecutor.NamedTask;
import com.theyawns.controller.Constants;
import com.theyawns.banking.fraud.fdengine.jetimpl.sink.Graphite;

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
    private transient PNCounter walmart;
    private transient PNCounter amazon;
    private transient PNCounter latencyMillis;
    private transient PNCounter latencyItems;

    private transient Map<String, PNCounter> rejectionByRule;

    private transient Graphite graphite;
    private boolean initialized = false;
    private String host;

    private int measurementInterval = 5;   // seconds.   Should be in sync with schedule interval set by Launcher.

    private static int previouslyReportedCompletions = 0;

    public PumpGrafanaStats(String host) {
        this.host = host;
    }

    private void init() {
        approvalCounter = hazelcast.getPNCounter(Constants.PN_COUNT_APPROVED);
        rejectedForCredit = hazelcast.getPNCounter(Constants.PN_COUNT_REJ_CREDIT);
        rejectedForFraud = hazelcast.getPNCounter(Constants.PN_COUNT_REJ_FRAUD);
        walmart = hazelcast.getPNCounter(Constants.PN_COUNT_WALMART);
        amazon = hazelcast.getPNCounter(Constants.PN_COUNT_AMAZON);
        latencyMillis = hazelcast.getPNCounter(Constants.PN_COUNT_TOTAL_LATENCY);
        latencyItems = hazelcast.getPNCounter(Constants.PN_COUNT_LATENCY_ITEMS);
        graphite = new Graphite(host);
        rejectionByRule = new HashMap<>();
        initialized = true;
        lastTimeRun = System.currentTimeMillis();

        findCounters(); // move call into run loop if this changes dynamically; it doesn't currently.
        //System.out.println("PumpGrafanaStats.init() complete");
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
                //System.out.println("Found rule-specific counter " + dobj.getName());
            }
        }
    }

    long lastTimeRun;

    // Runs at intervals
    @Override
    public void run() {
    	//System.out.println("PumpGrafanaStats active");
        if (!initialized)
            init();

        //findCounters();    Now done just once in init()

        // counters are integers but we want to do floating-point math with the results
        double approved = (double) approvalCounter.get();
        double rejectedFraud = (double) rejectedForFraud.get();
        double rejectedCredit = (double) rejectedForCredit.get();
        //double completions = approved + rejectedFraud + rejectedCredit;
        double completions = (double)  latencyItems.get();
        double fraudRate = 0.0;
        if (completions > 0)
            fraudRate = rejectedFraud / completions;
        // Divide by 1 Mil to convert nanos to millis
        double latencyAvg = latencyMillis.get() / completions;
        try {
            graphite.writeStats("bib.fraud.rate", fraudRate);
            graphite.writeStats("bib.payment.rate", ((completions - previouslyReportedCompletions) / measurementInterval)); // divide to convert to TPS
            previouslyReportedCompletions = (int) completions;
            graphite.writeStats("bib.payments.amazon", amazon.get());
            graphite.writeStats("bib.payments.walmart", walmart.get());
            graphite.writeStats("bib.payments.all", completions);
            graphite.writeStats("bib.latency.avg", latencyAvg);
            if (rejectionByRule != null && rejectionByRule.size() > 0) {
                for (PNCounter counter : rejectionByRule.values()) {
                    graphite.writeStats("bib.rejectedby." + counter.getName(), counter.get());
                }
            } else {
                //System.out.println("Skipping rule specific counters because " + ((rejectionByRule == null) ? "null" : "empty"));
            }

        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("** Reinitializing Graphite");
            graphite = new Graphite(host);
        }

        lastTimeRun = System.currentTimeMillis();
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
