FROM hazelcast/docker-grafana-graphite:latest

RUN rm /src/dashboards/flight-telemetry.json

COPY target/classes/bankinabox-dashboard.json      /src/dashboards

