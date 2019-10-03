FROM hazelcast/docker-grafana-graphite:latest

# Not relevant
RUN rm /src/dashboards/flight-telemetry.json
# To be replaced
RUN rm /src/dashboards/Jet.json

COPY target/classes/bankinabox-dashboard.json      /src/dashboards
COPY target/classes/Jet.json                       /src/dashboards

