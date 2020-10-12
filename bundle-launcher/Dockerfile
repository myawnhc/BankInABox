FROM library/openjdk:11-jre-slim

ARG JAR_FILE
COPY target/${JAR_FILE} application.jar

COPY target/classes/hazelcast-client.xml hazelcast-client.xml

ENV JAVA_MODS "--add-modules java.se --add-exports java.base/jdk.internal.ref=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED --add-opens java.management/sun.management=ALL-UNNAMED --add-opens jdk.management/com.sun.management.internal=ALL-UNNAMED"

ENV HZ_IS_IMDG "true"
ENV HZ_IP4_ADDR "none"
ENV HZ_KUBERNETES_ENABLED "true"
ENV HZ_TCP_ENABLED "false"
# Can override values in the config file with these but not needed for current test
#ENV HZ_IS_CE "true"        bib.cloud CLI setting makes this redundant
#ENV HZCE_TOKEN "ecrQtKtBTMgG39mR2MrxeeJvxZLWMNpHHdVefG9H0n7ukP61UO"
#ENV HZCE_URLBASE "https://uat.hazelcast.cloud"

ENV JAVA_ARGS ""
ENV JAVA_ARGS "$JAVA_ARGS -Dhz.ip4.addr=$HZ_IP4_ADDR"
ENV JAVA_ARGS "$JAVA_ARGS -Dhz.is.imdg=$HZ_IS_IMDG"
ENV JAVA_ARGS "$JAVA_ARGS -Dhz.kubernetes.enabled=$HZ_KUBERNETES_ENABLED"
ENV JAVA_ARGS "$JAVA_ARGS -Dhz.tcp.enabled=$HZ_TCP_ENABLED"
#ENV JAVA_ARGS "$JAVA_ARGS -Dhz.is.cloud.enterprise=$HZ_IS_CE"
#ENV JAVA_ARGS "$JAVA_ARGS -Dhz.ce.discovery.token=$HZCE_TOKEN"
#ENV JAVA_ARGS "$JAVA_ARGS -Dhz.ce.urlbase=$HZCE_URLBASE"

# Options: none, hzce_aws, hzce_azure
ENV LAUNCHER_ARGS "bib.cloud=none"
# Options benchmark, demo
ENV LAUNCHER_ARGS "$LAUNCHER_ARGS bib.runmode=benchmark"
ENV LAUNCHER_ARGS "$LAUNCHER_ARGS bib.peered=false"

# No default for namespace
ENTRYPOINT exec java $JAVA_MODS $JAVA_ARGS -jar application.jar $LAUNCHER_ARGS
