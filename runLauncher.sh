#!/usr/bin/env bash

MAVEN_REPOSITORY=${HOME}/.m2/repository
CLASSPATH=${MAVEN_REPOSITORY}/com/hazelcast/jet/hazelcast-jet/3.1/hazelcast-jet-3.1.jar

#java -Dip4.addr=localhost -Xms8g -Xmx8g -classpath "./src/main/resources:${CLASSPATH}:./target/BankInABox-1.0-SNAPSHOT.jar" com.theyawns.launcher.Launcher
java -Dip4.addr=localhost -Xms8g -Xmx8g -classpath "./src/main/resources:${CLASSPATH}:./the-bank/target/the-bank-1.0-SNAPSHOT.jar" com.theyawns.launcher.Launcher
