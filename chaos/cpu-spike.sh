#!/bin/bash
# chaos/cpu-spike.sh
#
# Pegs CPU inside a target container by running busy loops via `docker exec`
# (no extra tools required — works against the plain JRE image).
#
# Usage:
#   ./chaos/cpu-spike.sh <service-name> <num-cores> <duration-seconds>
#   ./chaos/cpu-spike.sh fraud-service 2 90
#
# What to observe:
#   - Grafana: process_cpu_usage / system_cpu_usage for the target service
#     spikes toward 1.0 (100%)
#   - HighCpuUsage alert fires after 5 minutes sustained
#   - Latency: p95/p99 for that service (and its callers) rises even though
#     nothing is "broken" — just slow, which is a different failure mode
#     than errors and is worth being able to tell apart in the dashboards

set -euo pipefail
SERVICE=${1:?Usage: $0 <service-name> <num-cores> <duration-seconds>}
CORES=${2:?Usage: $0 <service-name> <num-cores> <duration-seconds>}
DURATION=${3:?Usage: $0 <service-name> <num-cores> <duration-seconds>}

CONTAINER=$(docker compose ps -q "$SERVICE")
if [ -z "$CONTAINER" ]; then
  echo "$SERVICE container not found. Is the stack running?"
  exit 1
fi

echo "Spinning up $CORES busy-loop(s) in $SERVICE for ${DURATION}s..."
docker exec -d "$CONTAINER" sh -c "
  for i in \$(seq 1 $CORES); do
    ( t0=\$(date +%s); while [ \$(( \$(date +%s) - t0 )) -lt $DURATION ]; do :; done ) &
  done
"

echo "Busy loops started in background inside the container. They'll self-terminate after ${DURATION}s."
echo "Watch Grafana now."
