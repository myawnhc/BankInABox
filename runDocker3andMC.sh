#!/usr/bin/env bash

HOST=localhost
MANCENTER_OPT=MANCENTER_URL="http://${HOST}:8080/hazelcast-mancenter"
# Set CONFIG_OPT to empty to get startup to work, then can poke around inside container.  Have verified ENV variable is
# set and jar file is in the proper location, but still getting CNFE on IDSFactory at container startup.
CONFIG_OPT="-Dhazelcast.config=/data/hazelcast/hazelcast.xml"
CONFIG_VOLUME="${PWD}/data/hazelcast:/data/hazelcast:ro"   # read-only config volume iniitialized from project config file
USER_CODE_VOLUME="${PWD}/usercode:/opt/hazelcast/usercode"

# Copy config file to mounted volume location
cp hazelcast.xml data/hazelcast/
# Copy user code to mounted volume location
cp target/BankInABox-1.0-SNAPSHOT.jar usercode/

#docker run -p 8080:8080 hazelcast/management-center > /tmp/mancenter.log &
docker run -p 5701:5701 \
           -e ${MANCENTER_OPT} \
           -e JAVA_OPTS="-Dhazelcast.local.publicAddress=${HOST}:5701 ${CONFIG_OPT}" \
           -e CLASSPATH="/opt/hazelcast/usercode/" \
           -v ${CONFIG_VOLUME} \
           -v ${USER_CODE_VOLUME} \
           hazelcast/hazelcast > /tmp/node1.log &
#docker run -p 5702:5702 -e MANCENTER_URL="http://${HOST}:8080/hazelcast-mancenter" -e JAVA_OPTS="-Dhazelcast.local.publicAddress=${HOST}:5702" hazelcast/hazelcast &
#docker run -p 5703:5703 -e MANCENTER_URL="http://${HOST}:8080/hazelcast-mancenter" -e JAVA_OPTS="-Dhazelcast.local.publicAddress=${HOST}:5703" hazelcast/hazelcast &


