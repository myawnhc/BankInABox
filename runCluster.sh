#!/usr/bin/env bash
java -Xms8g -Xmx8g -classpath "./lib/*:./resources/*:./target/RuleEngine-1.0-SNAPSHOT.jar" com.theyawns.launcher.ClusterMember
