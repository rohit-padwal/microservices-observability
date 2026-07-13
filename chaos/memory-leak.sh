#!/bin/bash
# chaos/memory-leak.sh
#
# Drives payment-service's opt-in /internal/chaos/leak endpoint to grow its
# heap steadily, simulating a slow memory leak. That endpoint only exists
# when payment-service is started with SPRING_PROFILES_ACTIVE=chaos — set
# that in docker-compose.yml (or override it) before running this.
#
# Usage:
#   ./chaos/memory-leak.sh <iterations> <mb-per-call> <interval-seconds>
#   ./chaos/memory-leak.sh 60 10 2      # ~600MB over ~2 minutes
#
# What to observe:
#   - Grafana: jvm_memory_used_bytes{application="payment-service"} climbs
#     steadily and doesn't drop between GC cycles (a real leak, not just
#     GC pressure)
#   - HighJvmHeapUsage alert fires once heap usage passes 85%
#   - Eventually: OutOfMemoryError in payment-service logs, then the
#     container may be OOM-killed by Docker — watch ServiceDown fire right
#     after
#
# Reset without restarting the container:
#   curl -X POST http://localhost:8083/internal/chaos/reset
#   (or, more simply): docker compose restart payment-service

set -euo pipefail
ITERATIONS=${1:?Usage: $0 <iterations> <mb-per-call> <interval-seconds>}
MB_PER_CALL=${2:?Usage: $0 <iterations> <mb-per-call> <interval-seconds>}
INTERVAL=${3:?Usage: $0 <iterations> <mb-per-call> <interval-seconds>}

# Hits payment-service directly (container-to-container port), bypassing
# the gateway since this is an internal-only debug endpoint.
URL="http://localhost:8083/internal/chaos/leak?megabytes=${MB_PER_CALL}"

echo "Leaking ~${MB_PER_CALL}MB every ${INTERVAL}s for ${ITERATIONS} iterations..."
for i in $(seq 1 "$ITERATIONS"); do
  curl -s -X POST "$URL" | python3 -m json.tool || true
  sleep "$INTERVAL"
done

echo "Done. Reset with: curl -X POST http://localhost:8083/internal/chaos/reset"
