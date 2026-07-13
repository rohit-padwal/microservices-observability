// load-tests/order-flow.js
//
// Simulates a realistic customer flow through the gateway:
//   1. POST /api/orders   (order-service -> payment -> fraud -> db -> notification)
//   2. GET  /api/orders/{id}
//
// Usage:
//   k6 run load-tests/order-flow.js                     # uses default stage (see below)
//   k6 run -e SCENARIO=smoke_10   load-tests/order-flow.js
//   k6 run -e SCENARIO=load_100   load-tests/order-flow.js
//   k6 run -e SCENARIO=stress_1000 load-tests/order-flow.js
//   k6 run -e SCENARIO=soak_5000  load-tests/order-flow.js
//
// Set GATEWAY_URL if the gateway isn't on localhost:8080.

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';

const BASE_URL = __ENV.GATEWAY_URL || 'http://localhost:8080';

const orderCreateDuration = new Trend('order_create_duration', true);
const orderFailures = new Counter('order_failures');

const scenarios = {
  // 10 concurrent users — sanity check that the whole pipeline works.
  smoke_10: {
    executor: 'constant-vus',
    vus: 10,
    duration: '2m',
  },
  // 100 concurrent users — normal expected load.
  load_100: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '1m', target: 100 },
      { duration: '3m', target: 100 },
      { duration: '1m', target: 0 },
    ],
  },
  // 1000 concurrent users — find the first bottleneck.
  stress_1000: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '2m', target: 1000 },
      { duration: '5m', target: 1000 },
      { duration: '2m', target: 0 },
    ],
  },
  // 5000 concurrent users — push until something breaks; pair this with
  // the chaos scripts to see how the stack surfaces the failure.
  soak_5000: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '3m', target: 5000 },
      { duration: '10m', target: 5000 },
      { duration: '3m', target: 0 },
    ],
  },
};

const selected = __ENV.SCENARIO || 'smoke_10';

export const options = {
  scenarios: {
    [selected]: scenarios[selected],
  },
  thresholds: {
    http_req_duration: ['p(95)<500'],
    http_req_failed: ['rate<0.02'],
  },
};

export default function () {
  const userId = Math.floor(Math.random() * 10000) + 1;
  const amount = (Math.random() * 4800 + 10).toFixed(2); // 10.00 - 4810.00, crosses fraud thresholds

  const payload = JSON.stringify({
    userId: userId,
    itemName: 'demo-item',
    quantity: Math.floor(Math.random() * 3) + 1,
    totalAmount: amount,
  });

  const params = { headers: { 'Content-Type': 'application/json' } };

  const res = http.post(`${BASE_URL}/api/orders`, payload, params);
  orderCreateDuration.add(res.timings.duration);

  const ok = check(res, {
    'order created (201)': (r) => r.status === 201,
  });

  if (!ok) {
    orderFailures.add(1);
  } else {
    const orderId = JSON.parse(res.body).id;
    http.get(`${BASE_URL}/api/orders/${orderId}`);
  }

  sleep(Math.random() * 1.5);
}
