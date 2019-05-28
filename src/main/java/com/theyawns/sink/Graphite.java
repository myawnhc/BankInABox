package com.theyawns.sink;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.Socket;

public class Graphite {
    //graphite socket
    private static String HOST="localhost";
    private static int PORT=2003;
    Socket graphiteSocket;

    public Graphite(){
        // create graphite socket
        try {
            graphiteSocket = new Socket(HOST,PORT);
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
