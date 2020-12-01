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

JAVA_MODS="--add-modules java.se --add-exports java.base/jdk.internal.ref=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED --add-opens java.management/sun.management=ALL-UNNAMED --add-opens jdk.management/com.sun.management.internal=ALL-UNNAMED"

JAVA_ARGS="-Dhz.is.imdg=true"
JAVA_ARGS="${JAVA_ARGS} -Dhz.ip4.addr=localhost"
JAVA_ARGS="${JAVA_ARGS} -Xms8G -Xmx8g"
JAVA_ARGS="${JAVA_ARGS} -XX:+FlightRecorder"
JAVA_ARGS="${JAVA_ARGS} -XX:StartFlightRecording=disk=true,dumponexit=true,filename=recording-imdg.jfr,maxsize=1024m,maxage=1d,settings=profile"

java $JAVA_MODS $JAVA_ARGS -jar bundle-imdg/target/bundle-imdg-1.0-SNAPSHOT.jar
