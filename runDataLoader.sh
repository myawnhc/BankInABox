#!/usr/bin/env bash
# NOTE: DEPRECATED -- now runPreloader.sh is used to pre-generate the data and write it to a database; this
# script was used to generate data concurrently with the Launcher running and processing the data
java -Xms8g -Xmx8g -classpath "./src/main/resources:./lib/hazelcast-jet-3.0.jar:./target/BankInABox-1.0-SNAPSHOT.jar" com.theyawns.domain.payments.TransactionGenerator
