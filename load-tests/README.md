# Load Testing (k6)

`order-flow.js` drives the full request path: **Gateway → Order → Payment →
Fraud → Database → Notification**, exactly the trace you'll see in Tempo.

## Install k6

```bash
# macOS
brew install k6

# Debian/Ubuntu
sudo gpg -k
sudo gpg --no-default-keyring --keyring /usr/share/keyrings/k6-archive-keyring.gpg \
  --keyserver hkp://keyserver.ubuntu.com:80 --recv-keys C5AD17C747E3415A3642D57D77C6C491D6ACFD8
echo "deb [signed-by=/usr/share/keyrings/k6-archive-keyring.gpg] https://dl.k6.io/deb stable main" \
  | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update && sudo apt-get install k6
```

## Run a scenario

```bash
k6 run -e SCENARIO=smoke_10    load-tests/order-flow.js   # 10 users,   2 min
k6 run -e SCENARIO=load_100    load-tests/order-flow.js   # 100 users,  5 min
k6 run -e SCENARIO=stress_1000 load-tests/order-flow.js   # 1000 users, 9 min
k6 run -e SCENARIO=soak_5000   load-tests/order-flow.js   # 5000 users, 16 min
```

If the gateway isn't on `localhost:8080`, pass `-e GATEWAY_URL=http://your-host:port`.

## What to watch while it runs

| Where | What to look at |
|---|---|
| Grafana → Service Overview dashboard | requests/sec, error %, p95/p99 latency per service |
| Grafana → Payment Dashboard | payment success/fail split, fraud decline rate, DB latency |
| Grafana → Infrastructure dashboard | container CPU/memory, `notification_queue_size` |
| Tempo | pick a slow trace and see which hop (fraud? DB? notification?) ate the time |
| VictoriaLogs (Grafana Explore) | filter by `error_code` to see what's failing under load |

## Recording results

After each run, capture (see the README's "Scaling Experiment" section for
the table to fill in):
- k6's own summary (req/s, p95, error rate) — printed at the end of the run
- Grafana screenshots of the same window
- Which component's dashboard/alert fired first as load increased

This is intentionally left for you to run against your own hardware — the
bottleneck you hit (DB connection pool, JVM heap, single Postgres instance,
the notification queue, NGINX worker connections, etc.) will depend on the
resources you give the containers.
