FROM adoptopenjdk/openjdk11

ARG JAR_FILE
COPY target/${JAR_FILE} application.jar

COPY target/classes/hazelcast.xml hazelcast.xml

ENV JAVA_MODS "--add-modules java.se --add-exports java.base/jdk.internal.ref=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED --add-opens java.management/sun.management=ALL-UNNAMED --add-opens jdk.management/com.sun.management.internal=ALL-UNNAMED"

ENV HZ_TCP_ENABLED "false"
ENV HZ_IP4_ADDR "none"

ENV JAVA_ARGS "-Dhz.tcp.enabled=$HZ_TCP_ENABLED"
ENV JAVA_ARGS "$JAVA_ARGS -Dhz.ip4.addr=$HZ_IP4_ADDR"

ENTRYPOINT exec java $JAVA_MODS $JAVA_ARGS -jar application.jar
