#!/usr/bin/env bash
# DEPRECATED
java -Xms8g -Xmx8g -classpath "./src/main/resources:./lib/hazelcast-jet-3.0.jar:./target/BankInABox-1.0-SNAPSHOT.jar" com.theyawns.launcher.DualLauncher
