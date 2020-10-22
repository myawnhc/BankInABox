#!/usr/bin/env bash

JAVA_MODS="--add-modules java.se --add-exports java.base/jdk.internal.ref=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED --add-opens java.management/sun.management=ALL-UNNAMED --add-opens jdk.management/com.sun.management.internal=ALL-UNNAMED"

JAVA_ARGS="-Dhz.is.imdg=true"
JAVA_ARGS="${JAVA_ARGS} -Dhz.ip4.addr=localhost"
JAVA_ARGS="${JAVA_ARGS} -Xms8G -Xmx8g"
JAVA_ARGS="${JAVA_ARGS} -XX:+FlightRecorder"
JAVA_ARGS="${JAVA_ARGS} -XX:StartFlightRecording=disk=true,dumponexit=true,filename=recording-imdg.jfr,maxsize=1024m,maxage=1d,settings=profile"

java $JAVA_MODS $JAVA_ARGS -jar bundle-imdg/target/bundle-imdg-1.0-SNAPSHOT.jar
