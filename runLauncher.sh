#!/usr/bin/env bash

JAVA_MODS="--add-modules java.se --add-exports java.base/jdk.internal.ref=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED --add-opens java.management/sun.management=ALL-UNNAMED --add-opens jdk.management/com.sun.management.internal=ALL-UNNAMED"

JAVA_ARGS=""
JAVA_ARGS="${JAVA_ARGS} -Dip4.addr=localhost"
JAVA_ARGS="${JAVA_ARGS} -Xms8G -Xmx8g"

java $JAVA_MODS $JAVA_ARGS -jar bundle-launcher/target/bundle-launcher-1.0-SNAPSHOT.jar
