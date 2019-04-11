package com.theyawns.domain.payments;

import com.hazelcast.jet.datamodel.KeyedWindowResult;
import com.hazelcast.jet.pipeline.Pipeline;
import com.hazelcast.jet.pipeline.Sinks;
import com.hazelcast.jet.pipeline.StreamStage;
import com.hazelcast.jet.pipeline.Sink;
import static com.hazelcast.jet.pipeline.SinkBuilder.sinkBuilder;
import com.hazelcast.jet.datamodel.KeyedWindowResult;

import org.python.core.PyFloat;
import org.python.core.PyInteger;
import org.python.core.PyList;
import org.python.core.PyString;
import org.python.core.PyTuple;
import org.python.modules.cPickle;

import java.io.BufferedOutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.function.Consumer;

public class LocationBasedRule extends BaseRule {

    @Override
    Pipeline buildPipeline() {
        Pipeline p = Pipeline.create();
        StreamStage<TransactionWithRules> enrichedJournal = getEnrichedJournal(p);

        // TODO: copy applicable processing stages from JetRuleEngine
        enrichedJournal.drainTo(Sinks.logger());

        return p;
    }

    public static void main(String[] args) {
        LocationBasedRule rule = new LocationBasedRule();
        rule.run();
    }

    /**
     * Sink implementation which forwards the items it receives to the Graphite.
     * Graphite's Pickle Protocol is used for communication between Jet and Graphite.
     *
     * @param host Graphite host
     * @param port Graphite port
     */
    private static Sink<KeyedWindowResult> buildGraphiteSink(String host, int port) {
        return sinkBuilder("graphite", instance ->
                new BufferedOutputStream(new Socket(host, port).getOutputStream()))
                .<KeyedWindowResult>receiveFn((bos, entry) -> {
                    GraphiteMetric metric = new GraphiteMetric();
                    metric.from(entry);

                    PyString payload = cPickle.dumps(metric.getAsList(), 2);
                    byte[] header = ByteBuffer.allocate(4).putInt(payload.__len__()).array();

                    bos.write(header);
                    bos.write(payload.toBytes());
                })
                .flushFn(BufferedOutputStream::flush)
                .destroyFn(BufferedOutputStream::close)
                .build();
    }

    /**
     * A data transfer object for Graphite
     */
    private static class GraphiteMetric {
        PyString metricName;
        PyInteger timestamp;
        PyFloat metricValue;

        private GraphiteMetric() {
        }

        private void fromTransactionEntry(KeyedWindowResult<Long, Transaction> transactionEntry) {
            Transaction transaction = transactionEntry.getValue();

            metricName = new PyString(replaceWhiteSpace(
                    transaction.getPaymentResult()  + "." +
                    transaction.getFraudResult() );

            timestamp = new PyInteger(getEpochSecond(
                    transaction.getPosTime()));

            metricValue = new PyFloat(1);
        }

        // Graph Transaction Results (approved/not)
        // Graph

        private void fromMaxNoiseEntry(KeyedWindowResult<String, Integer> entry) {
            metricName = new PyString(replaceWhiteSpace(entry.getKey()));
            timestamp = new PyInteger(getEpochSecond(entry.end()));
            metricValue = new PyFloat(entry.getValue());
        }

        private void fromTotalC02Entry(KeyedWindowResult<String, Double> entry) {
            metricName = new PyString(replaceWhiteSpace(entry.getKey()));
            timestamp = new PyInteger(getEpochSecond(entry.end()));
            metricValue = new PyFloat(entry.getValue());
        }

        void from(KeyedWindowResult entry) {
            if (entry.getKey() instanceof Long) {
                KeyedWindowResult<Long, Aircraft> aircraftEntry = entry;
                fromAirCraftEntry(aircraftEntry);
            } else {
                if (entry.getValue() instanceof Double) {
                    fromTotalC02Entry(entry);
                } else {
                    fromMaxNoiseEntry(entry);
                }
            }
        }

        PyList getAsList() {
            PyList list = new PyList();
            PyTuple metric = new PyTuple(metricName, new PyTuple(timestamp, metricValue));
            list.add(metric);
            return list;
        }

        private int getEpochSecond(long millis) {
            return (int) Instant.ofEpochMilli(millis).getEpochSecond();
        }

        private String replaceWhiteSpace(String string) {
            return string.replace(" ", "_");
        }
    }

}