#!/bin/bash
# chaos/database-unavailable.sh
#
# Stops the shared PostgreSQL container to simulate a full database outage
# affecting every service at once — the worst-case scenario in this
# architecture since all 4 services depend on the same instance.
#
# Usage:
#   ./chaos/database-unavailable.sh <duration-seconds>
#   ./chaos/database-unavailable.sh 60
#
# What to observe:
#   - Prometheus: DatabaseDown alert fires almost immediately (pg_up == 0)
#   - Grafana Service Overview: every service's error rate climbs together
#     (a strong visual signal that this is a shared-dependency failure,
#     not one service misbehaving)
#   - Notification queue: notification_queue_size and
#     notification_processing_queue_size climb since writes start failing
#     and retries/backpressure build up
#   - Logs: look for "Connection refused" / "connection to database failed"
#     across all 4 services' JSON logs in the same time window

set -euo pipefail
DURATION=${1:?Usage: $0 <duration-seconds>}

echo "Stopping postgres for ${DURATION}s..."
docker compose stop postgres

sleep "$DURATION"

echo "Restarting postgres..."
docker compose start postgres
echo "postgres is back. Give services ~10-20s to reconnect their pools."
