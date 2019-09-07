#!/usr/bin/env bash
java -Dip4.addr=localhost -Xms8g -Xmx8g -classpath "./src/main/resources:./lib/hazelcast-jet-3.0.jar:./lib/mariadb-java-client-2.4.3.jar:./target/BankInABox-1.0-SNAPSHOT.jar" com.theyawns.launcher.JetMain
