#!/usr/bin/env bash

#
#  Copyright 2018-2021 Hazelcast, Inc
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#         http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.package com.theyawns.controller.launcher;
#

# TODO: find a way of getting host that works on as many platforms as possible ... this works on MacOS, ?? on others
# locahost or 127.0.0.1 resulted in disconnected nodes that would not form a cluster
HOST=`ipconfig getifaddr en0`
MANCENTER_OPT=MANCENTER_URL="http://${HOST}:8080/hazelcast-mancenter"
CONFIG_OPT="-Dhazelcast.config=/data/hazelcast/hazelcast.xml"
CONFIG_VOLUME="${PWD}/data/hazelcast:/data/hazelcast:ro"   # read-only config volume initialized from project config file
USER_CODE_VOLUME="${PWD}/usercode:/opt/hazelcast/usercode"

# Copy config file to mounted volume location
cp hazelcast.xml data/hazelcast/
# Copy user code to mounted volume location
cp target/BankInABox-1.0-SNAPSHOT.jar usercode/

docker run -p 8080:8080 --name mancenter --rm hazelcast/management-center > /tmp/mancenter.log &

docker run -p 5701:5701 \
           -e ${MANCENTER_OPT} \
           -e JAVA_OPTS="-Dhazelcast.local.publicAddress=${HOST}:5701 ${CONFIG_OPT} -Dip4.addr=${HOST}" \
           -e CLASSPATH="/opt/hazelcast/usercode/*" \
           -v ${CONFIG_VOLUME} \
           -v ${USER_CODE_VOLUME} \
           --name member1 \
           --rm \
           hazelcast/hazelcast > /tmp/node1.log &

docker run -p 5702:5701 \
           -e ${MANCENTER_OPT} \
           -e JAVA_OPTS="-Dhazelcast.local.publicAddress=${HOST}:5702 ${CONFIG_OPT} -Dip4.addr=${HOST}" \
           -e CLASSPATH="/opt/hazelcast/usercode/*" \
           -v ${CONFIG_VOLUME} \
           -v ${USER_CODE_VOLUME} \
           --name member2 \
           --rm \
           hazelcast/hazelcast > /tmp/node2.log &

docker run -p 5703:5701 \
           -e ${MANCENTER_OPT} \
           -e JAVA_OPTS="-Dhazelcast.local.publicAddress=${HOST}:5703 ${CONFIG_OPT} -Dip4.addr=${HOST}" \
           -e CLASSPATH="/opt/hazelcast/usercode/*" \
           -v ${CONFIG_VOLUME} \
           -v ${USER_CODE_VOLUME} \
           --name member3 \
           --rm \
           hazelcast/hazelcast > /tmp/node3.log &


