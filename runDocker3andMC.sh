#!/usr/bin/env bash
#docker run -e JAVA_OPTS="-Dhazelcast.config=/opt/hazelcast/config_ext/hazelcast.xml" -v PATH_TO_LOCAL_CONFIG_FOLDER:/opt/hazelcast/config_ext hazelcast/hazelcast

HOST=localhost
MANCENTER_OPT=MANCENTER_URL="http://${HOST}:8080/hazelcast-mancenter"
CONFIG_OPT="-Dhazelcast.config=/data/hazelcast/hazelcast.xml"
CONFIG_PATH="${PWD}/data/hazelcast:/data/hazelcast:ro"   # read-only config volume iniitialized from project config file
JAR_PATH="${PWD}/target/BankInABox-1.0-SNAPSHOT.jar:/lib"

# Copy config file to mounted volume location
cp hazelcast.xml data/hazelcast/

#docker run -p 8080:8080 hazelcast/management-center > /tmp/mancenter.log &
docker run -p 5701:5701 \
           -e ${MANCENTER_OPT} \
           -e JAVA_OPTS="-Dhazelcast.local.publicAddress=${HOST}:5701 ${CONFIG_OPT}" \
           -e CLASSPATH="/lib" \
           -v ${CONFIG_PATH} -v ${JAR_PATH} hazelcast/hazelcast > /tmp/node1.log &
#docker run -p 5702:5702 -e MANCENTER_URL="http://${HOST}:8080/hazelcast-mancenter" -e JAVA_OPTS="-Dhazelcast.local.publicAddress=${HOST}:5702" hazelcast/hazelcast &
#docker run -p 5703:5703 -e MANCENTER_URL="http://${HOST}:8080/hazelcast-mancenter" -e JAVA_OPTS="-Dhazelcast.local.publicAddress=${HOST}:5703" hazelcast/hazelcast &


