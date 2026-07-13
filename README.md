# Observability Platform

A hands-on microservices + observability sandbox: 4 Spring Boot services behind
an NGINX gateway, fully instrumented with OpenTelemetry, and a complete
metrics/logs/traces/alerting stack to watch it all — including chaos-testing
and load-testing tooling so you can break it on purpose and see how the
stack tells you what happened.

## Architecture

```
                                Users
                                  │
                                  ▼
                         ┌─────────────────┐
                         │  API Gateway     │
                         │  (NGINX)         │
                         └────────┬────────┘
                                  │
                                  ▼
                         ┌─────────────────┐
                         │  Order Service   │
                         └────────┬────────┘
                                  │
                                  ▼
                         ┌─────────────────┐
                         │  Payment Service │
                         └───┬─────────┬───┘
                             │         │
                 ┌───────────┘         └───────────┐
                 ▼                                  ▼
        ┌─────────────────┐               ┌──────────────────────┐
        │  Fraud Detection │               │  Notification Service │
        └────────┬────────┘               └───────────┬──────────┘
                 │                                     │
                 ▼                                     ▼
        ┌────────────────────────────────────────────────────┐
        │                    PostgreSQL                      │
        │      (orderdb · paymentdb · frauddb · notificationdb)│
        └────────────────────────────────────────────────────┘
```

One "place an order" request produces exactly the trace you'd expect to
debug in production:

```
Gateway → Order → Payment → Fraud → (Postgres) → Notification (async)
```

Payment calls Fraud **synchronously** (it needs the verdict before settling).
Payment calls Notification **asynchronously**, through an in-process bounded
queue (a stand-in for Kafka/RabbitMQ/SQS) — so a notification backlog never
slows down the customer-facing payment response.

### Why 4 services, not 5

The original spec listed 5 (API Gateway, Payment, Order, Fraud, Notification).
NGINX *is* the gateway here — it's a reverse proxy, not a JVM service, so
there's nothing to "instrument" beyond its own access logs and
`stub_status` metrics, both of which are wired in. The 4 Spring Boot services
are Order, Payment, Fraud, and Notification.

## Tech stack

| Layer | Technology |
|---|---|
| Services | Java 21, Spring Boot 3.3 |
| Gateway | NGINX |
| Database | PostgreSQL 16 (one DB per service) |
| Tracing | OpenTelemetry (Micrometer Tracing bridge) → OTel Collector → Tempo |
| Metrics | Micrometer/Actuator → Prometheus → VictoriaMetrics |
| Logs | Logstash JSON encoder → stdout → OTel Collector (filelog + OTLP) → VictoriaLogs |
| Visualization | Grafana |
| Alerting | Prometheus Alertmanager → Slack / Email |
| Load testing | k6 |
| Chaos testing | Shell scripts using `docker kill`, `tc netem`, `docker stop`, busy-loops, and a guarded leak endpoint |
| Orchestration | Docker Compose (see note below on Kubernetes) |

## Repository structure

```
observability-platform/
├── microservices/
│   ├── order/            # Order Service (Java/Spring Boot)
│   ├── payment/           # Payment Service — calls fraud + notification
│   ├── fraud/              # Fraud Detection Service
│   └── notification/       # Notification Service (async queue worker)
├── gateway/
│   └── nginx.conf          # API Gateway config
├── db-init/
│   └── init-multiple-dbs.sh # Creates one DB per service on first boot
├── observability/
│   ├── otel-collector/     # OTLP + filelog receivers, exports to Tempo/VictoriaLogs
│   ├── prometheus/         # Scrape config + alert rules
│   ├── alertmanager/       # Slack/Email routing (template, rendered at startup)
│   ├── tempo/              # Trace storage config
│   └── grafana/            # Datasource + dashboard provisioning
├── load-tests/
│   └── order-flow.js       # k6 script: 10 / 100 / 1000 / 5000 VU scenarios
├── chaos/
│   ├── kill-payment-service.sh
│   ├── inject-latency.sh
│   ├── database-unavailable.sh
│   ├── cpu-spike.sh
│   └── memory-leak.sh
├── docker-compose.yml
├── .env.example
└── README.md
```

## How telemetry flows

**Traces.** Micrometer Tracing (bridged to OpenTelemetry) auto-instruments
every inbound HTTP request and outbound `RestClient` call. Trace context
(`traceparent` header) propagates automatically across service hops — no
manual header wiring in application code. Each service exports OTLP directly
to the OTel Collector, which forwards to Tempo. Tempo's metrics-generator
also derives span-metrics and a service graph, remote-written into
VictoriaMetrics — so you get an auto-generated service map for free.

**Metrics.** Spring Boot Actuator + Micrometer expose `/actuator/prometheus`
on every service (request rate, latency histograms, JVM heap, CPU, thread
pools — all built in). Prometheus scrapes every service plus
`postgres-exporter`, `cadvisor`, and the NGINX `nginx-exporter`, evaluates
alert rules locally, and remote-writes everything into VictoriaMetrics for
longer retention. Grafana queries VictoriaMetrics as its primary datasource.

**Logs.** Each service logs structured JSON to stdout via Logstash's Logback
encoder, including `trace_id`, `span_id`, `service`, `level`, `message`, and
business correlation fields (`user_id`, `order_id`, `payment_id`,
`error_code`) set explicitly via MDC in the service code. The OTel Collector
tails Docker's JSON log files (`filelog` receiver) and forwards everything to
VictoriaLogs over OTLP. In Grafana's Explore view (VictoriaLogs datasource,
LogsQL), you can filter by any of those fields directly, e.g.:

```
service:"payment-service" AND error_code:"FRAUD_DECLINE"
_time:5m AND payment_id:"482"
```

**Alerting.** Prometheus evaluates `alert-rules.yml` and pushes firing alerts
to Alertmanager, which routes to Slack and/or email based on severity.

## How to start

1. **Prerequisites:** Docker + Docker Compose, ~4GB free RAM for the stack.
2. Copy the environment template and fill in real values (Slack webhook,
   SMTP creds) — or leave the placeholders if you just want the demo running
   without real alert delivery:
   ```bash
   cp .env.example .env
   ```
3. Bring everything up:
   ```bash
   docker compose up -d --build
   ```
4. Wait ~30–60s for Postgres health checks and service startup, then place a
   test order through the gateway:
   ```bash
   curl -X POST http://localhost:8080/api/orders \
     -H "Content-Type: application/json" \
     -d '{"userId": 1, "itemName": "Widget", "quantity": 2, "totalAmount": 49.99}'
   ```
5. Open the UIs:

   | Tool | URL |
   |---|---|
   | Grafana | http://localhost:3000 (anonymous viewer access enabled) |
   | Prometheus | http://localhost:9090 |
   | Alertmanager | http://localhost:9093 |
   | Tempo (via Grafana) | Explore → Tempo datasource |
   | VictoriaLogs (via Grafana) | Explore → VictoriaLogs datasource |
   | VictoriaMetrics | http://localhost:8428/vmui |

6. Generate steady traffic while you explore:
   ```bash
   k6 run -e SCENARIO=load_100 load-tests/order-flow.js
   ```

### Kubernetes

This repo ships Docker Compose as the primary way to run it locally. Every
piece here (stateless Spring Boot services, standard OSS observability
components) maps cleanly onto Kubernetes — Deployments for the 4 services and
NGINX, a StatefulSet or managed instance for Postgres, and the observability
stack via the Prometheus Operator / Grafana / Tempo / VictoriaMetrics Helm
charts. That translation (Helm charts, `ServiceMonitor` CRDs, ingress config)
is intentionally left as a follow-up rather than included here, since it's a
meaningfully different scope of work from the Compose setup.

## Dashboards

Grafana auto-provisions three dashboards (no manual setup needed):

- **Service Overview** — requests/sec, error %, p50/p95/p99 latency, CPU,
  JVM heap, in-flight requests, per service, filterable by an `application`
  template variable.
- **Payment Dashboard** — successful vs. failed payments, payment failure
  rate gauge, fraud decision rate, fraud-service latency, Postgres connection
  count, and the notification queue depth (both the dispatch queue in
  payment-service and the processing queue in notification-service).
- **Infrastructure Dashboard** — per-container CPU/memory/network/filesystem
  (via cAdvisor), NGINX active connections and request rate, Postgres
  up/down, total container count.

## Example alerts

Defined in `observability/prometheus/alert-rules.yml`:

| Alert | Condition | Severity |
|---|---|---|
| `ServiceDown` | Any of the 4 services unreachable for 30s | critical |
| `DatabaseDown` | `pg_up == 0` for 30s | critical |
| `HighHttp5xxRate` | 5xx rate > 2% for 2m | warning |
| `HighRequestLatencyP95` | p95 latency > 500ms for 5m | warning |
| `HighCpuUsage` | Process CPU > 80% for 5m | warning |
| `HighJvmHeapUsage` | Heap used/max > 85% for 5m | warning |
| `PaymentFailureRateHigh` | >20% of payments failing over 10m | warning |
| `NotificationQueueBacklog` | Queue depth > 200 for 2m | warning |

Alertmanager routes `critical` to Slack `#alerts-critical` + email, and
`warning` to Slack `#alerts`, with inhibition so a critical alert suppresses
its corresponding warning for the same service.

## Example trace walkthrough

After placing an order, open Grafana → Explore → Tempo, and search by
`service.name=order-service`. The resulting trace shows:

```
order-service   POST /api/orders            [total duration]
 └─ payment-service  POST /api/payments      [payment processing]
     ├─ fraud-service  POST /api/fraud-checks  [fraud scoring + DB write]
     │   └─ postgres (via JDBC instrumentation)
     ├─ postgres (payment status write)
     └─ (async, separate trace) notification-service POST /api/notifications
         └─ postgres (notification write)
```

Note the async notification hop deliberately starts a **new** trace — that's
realistic: most real systems don't propagate trace context across a message
queue boundary unless they explicitly wire it up (which is itself a good
follow-up exercise: propagate `traceparent` through the in-memory queue in
`NotificationDispatcher` and confirm the traces link via span links).

## Chaos testing

Each script in `chaos/` includes what to watch for in its header comment.
Suggested order to run them in:

1. `./chaos/kill-payment-service.sh` — sudden hard failure
2. `./chaos/inject-latency.sh fraud-service 800 120` — slow dependency, not broken
3. `./chaos/database-unavailable.sh 60` — shared-dependency outage, blast radius across all 4 services
4. `./chaos/cpu-spike.sh fraud-service 2 90` — CPU-bound degradation
5. `./chaos/memory-leak.sh 60 10 2` — slow leak → OOM (requires `PAYMENT_SERVICE_PROFILE=chaos` in `.env`)

For each, capture: which alert fired first, how long it took, and what the
trace/log/metric signature looked like — that's the actual deliverable of a
chaos exercise, not just "it broke."

## Scaling experiment

Run each k6 scenario and record results here as you go:

| Users | Req/s achieved | p95 latency | Error rate | First bottleneck observed |
|---|---|---|---|---|
| 10 | | | | |
| 100 | | | | |
| 1000 | | | | |
| 5000 | | | | |

See `load-tests/README.md` for exact commands and what to watch in Grafana
while each run executes. This table is intentionally blank — the actual
numbers depend entirely on the CPU/RAM you allocate to Docker and the
machine you run it on, so filling it in yourself is the point of the
exercise. Likely candidates for the first bottleneck, in rough order of how
this stack is configured: the single shared Postgres instance (connection
pool exhaustion), the `notification-service` thread pool (only 2–3 workers
by default), NGINX `worker_connections` (1024), and JVM heap sizing (no
`-Xmx` set, so it defaults to 25% of container memory).

## Lessons learned

Leave this section for your own write-up after running the scaling and
chaos exercises. Things worth capturing:
- Which failure was easiest to diagnose from the dashboards alone, and which
  required digging into logs or traces?
- Did any alert fire *before* you noticed the problem, or only after?
- Where did the system degrade gracefully vs. fail hard?
- What would you change about the architecture (e.g., real message broker
  instead of the in-process queue, connection pooling tuning, circuit
  breakers between services) based on what you observed?
