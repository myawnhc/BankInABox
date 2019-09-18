#!/usr/bin/env bash

MAVEN_REPOSITORY=${HOME}/.m2/repository
CLASSPATH=${MAVEN_REPOSITORY}/com/hazelcast/jet/hazelcast-jet/3.1/hazelcast-jet-3.1.jar
CLASSPATH=${CLASSPATH}:${MAVEN_REPOSITORY}/org/mariadb/jdbc/mariadb-java-client/2.4.4/mariadb-java-client-2.4.4.jar

java -Dip4.addr=localhost -Xms8g -Xmx8g -classpath "./src/main/resources:${CLASSPATH}:./the-bank/target/the-bank-1.0-SNAPSHOT.jar" com.theyawns.launcher.JetMain
