FROM hazelcast/management-center:4.2020.10-ALPHA

# Args to pass to ENV, set by dockerfile-maven-plugin.
ARG MC_LICENSE
ARG MC_ADMINUSER
ARG MC_ADMINPASSWORD
ARG MC_CLUSTER1_NAME
ARG MC_CLUSTER1_ADDRESSLIST

# To check health, uses port 8081 but internal URL. Preconfigure license, logon/password.
ENV JAVA_OPTS="-Dhazelcast.mc.healthCheck.enable=true"
# -Dhazelcast.mc.prometheusExporter.enabled=true
# -Dhazelcast.mc.license=$MC_LICENSE "

# Used by /mc-start.sh to create admin user
ENV MC_ADMIN_USER=$MC_ADMINUSER
ENV MC_ADMIN_PASSWORD=$MC_ADMINPASSWORD

# Preconfigure cluster connections
ENV MC_CLUSTER1_NAME=$MC_CLUSTER1_NAME
ENV MC_CLUSTER1_ADDRESSLIST=$MC_CLUSTER1_ADDRESSLIST

# Start Management Center
CMD ["bash", "-c", "set -euo pipefail \
      && echo ./mc-conf.sh cluster add -H=${MC_DATA} --cluster-name=${MC_CLUSTER1_NAME} --member-addresses=${MC_CLUSTER1_ADDRESSLIST} \
      && ./mc-conf.sh cluster add -H=${MC_DATA} --cluster-name=${MC_CLUSTER1_NAME} --member-addresses=${MC_CLUSTER1_ADDRESSLIST} \
      && /mc-start.sh \
     "]
