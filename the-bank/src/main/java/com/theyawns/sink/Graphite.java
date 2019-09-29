package com.theyawns.sink;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.Socket;

import com.hazelcast.logging.ILogger;
import com.hazelcast.logging.Logger;
import com.theyawns.util.EnvironmentSetup;

public class Graphite {
    private static final ILogger log = Logger.getLogger(Graphite.class);
    private static final int PORT=2003;
    //graphite socket
    Socket graphiteSocket;

    public Graphite(){
        // create graphite socket
        try {
        	String host = System.getProperty(EnvironmentSetup.GRAFANA);
        	
        	if (host!=null && host.length() > 0) {
        		log.info("'" + EnvironmentSetup.GRAFANA + "'=='" + host + "'");
        	} else {
        		log.info("'" + EnvironmentSetup.GRAFANA + "'=='" + host
        				+ "', using localhost for Graphite.");
        		host = "localhost";
        	}
        	
            graphiteSocket = new Socket(host,PORT);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    // write stats to graphite that is then rendered in grafana
    public void writeStats(String name, long value) throws IOException {
        // graphite plain text socket: 'variable.name.to.plot value time-since-1970-seconds \n'
        String writeTo = name+" " + value + " " + (System.currentTimeMillis()/ 1000) + " \n";
        BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(graphiteSocket.getOutputStream()));
        bufferedWriter.write(writeTo);
        bufferedWriter.flush();
        //System.out.println(writeTo);
    }

    public void writeStats(String name, double value) throws IOException {
        // graphite plain text socket: 'variable.name.to.plot value time-since-1970-seconds \n'
        String writeTo = name+" " + value + " " + (System.currentTimeMillis()/ 1000) + " \n";
        BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(graphiteSocket.getOutputStream()));
        bufferedWriter.write(writeTo);
        bufferedWriter.flush();
    }

}
