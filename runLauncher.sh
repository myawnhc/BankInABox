#!/usr/bin/env bash
java -Xms8g -Xmx8g -classpath "./lib/hazelcast-jet-3.0.jar:./target/RuleEngine-1.0-SNAPSHOT.jar" com.theyawns.launcher.Launcher
