#!/bin/bash
# chaos/kill-payment-service.sh
#
# Kills the payment-service container outright (SIGKILL) to simulate a
# crash/OOM-kill. Docker Compose will restart it per its restart policy
# (add `restart: on-failure` in docker-compose.yml if you want that).
#
# What to observe:
#   - Prometheus: ServiceDown alert fires within ~30s (see alert-rules.yml)
#   - Grafana Service Overview: order-service error rate spikes as its
#     calls to payment-service start failing/timing out
#   - Tempo: traces for /api/orders during the outage show a failed span
#     for the payment-service hop
#   - VictoriaLogs: order-service logs show connection-refused errors
#     with error_code set

set -euo pipefail
CONTAINER=$(docker compose ps -q payment-service)

if [ -z "$CONTAINER" ]; then
  echo "payment-service container not found. Is the stack running?"
  exit 1
fi

echo "Killing payment-service container ($CONTAINER)..."
docker kill "$CONTAINER"
echo "Done. Watch Grafana/Prometheus/Tempo now. Restart with:"
echo "  docker compose up -d payment-service"
