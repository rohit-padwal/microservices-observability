#!/bin/bash
# chaos/inject-latency.sh
#
# Adds artificial network latency inside the fraud-service container using
# `tc netem`. This simulates a slow downstream dependency without touching
# any application code.
#
# Usage:
#   ./chaos/inject-latency.sh <service-name> <delay-ms> <duration-seconds>
#   ./chaos/inject-latency.sh fraud-service 800 120
#
# What to observe:
#   - Tempo: the fraud-service span in payment traces grows to ~<delay-ms>
#   - Grafana: HighRequestLatencyP95 alert fires for payment-service (since
#     it's a synchronous call) once p95 crosses 500ms
#   - Payment-service logs: no errors, just slower responses — latency
#     doesn't always show up as errors, which is the point of this drill

set -euo pipefail
SERVICE=${1:?Usage: $0 <service-name> <delay-ms> <duration-seconds>}
DELAY_MS=${2:?Usage: $0 <service-name> <delay-ms> <duration-seconds>}
DURATION=${3:?Usage: $0 <service-name> <delay-ms> <duration-seconds>}

CONTAINER=$(docker compose ps -q "$SERVICE")
if [ -z "$CONTAINER" ]; then
  echo "$SERVICE container not found. Is the stack running?"
  exit 1
fi

echo "Adding ${DELAY_MS}ms latency to $SERVICE for ${DURATION}s..."
# tc isn't in the slim JRE image, so we run it via a network-namespace-sharing
# helper container that does have iproute2.
docker run --rm --net=container:"$CONTAINER" --cap-add=NET_ADMIN nicolaka/netshoot \
  sh -c "tc qdisc add dev eth0 root netem delay ${DELAY_MS}ms && sleep ${DURATION} && tc qdisc del dev eth0 root netem"

echo "Latency injection window complete; qdisc removed."
