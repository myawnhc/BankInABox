package com.theyawns.domain.payments;

import com.hazelcast.jet.datamodel.KeyedWindowResult;
import com.hazelcast.jet.function.ComparatorEx;
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

    private static final String RESULT_MAP = "LocationBasedRule";

    private static final int SINK_PORT = 2004;
    private static String SINK_HOST;
    static {
        //System.setProperty("hazelcast.logging.type", "log4j");
        SINK_HOST = System.getProperty("SINK_HOST", "127.0.0.1");
    }


    @Override
    Pipeline buildPipeline() {
        Pipeline p = Pipeline.create();
        StreamStage<TransactionWithRules> enrichedJournal = getEnrichedJournal(p);

        // TODO: copy applicable processing stages from JetRuleEngine
        enrichedJournal.drainTo(Sinks.logger());

//        for all transactions
//                determine if location of transaction is near location of cellphone
//                group by MerchantID
//                aggregate
//                setName(this.getClass().getName())
//
//        StreamStage<KeyedWindowResult<String, Transaction>> transactions = p
//                .drawFrom()
//
//
        // Build Graphite sink
        Sink<KeyedWindowResult> graphiteSink = buildGraphiteSink(SINK_HOST, SINK_PORT);

        //enrichedJournal.drainTo(Sinks.map(RESULT_MAP));


        // Drain all results to the Graphite sink
        p.drainTo(graphiteSink, failedTransactions)
                .setName("graphiteSink");

        return p;
    }

    /**
     * Returns the Fraud Results
     *
     * @param
     * @return \
     */
    private static int getFraudResult(int fraudResult) {
        // your logic here
        return fraudResult;
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
                    //metric.from(entry);

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

        // Graph Transaction Results (approved/not)
        private void fromTransactionEntry(KeyedWindowResult<Long, Transaction> transactionEntry) {
            Transaction transaction = transactionEntry.getValue();

            metricName = new PyString(replaceWhiteSpace(
                    transaction.getPaymentResult()  + "." +
                    transaction.getFraudResult() ));

            timestamp = new PyInteger(getEpochSecond(
                    transaction.getRequestTime() ));

            metricValue = new PyFloat(1);
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