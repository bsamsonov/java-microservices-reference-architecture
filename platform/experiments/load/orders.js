// Order flow under an open-model load: place an order, read it back, cancel one in ten.
// Run through run.sh; profiles and the reasoning behind them: README.md.
import http from 'k6/http';
import { check } from 'k6';
import exec from 'k6/execution';

const BASE = __ENV.ORDERS_URL || 'http://orders.localhost:8080';
const KEYCLOAK = __ENV.KEYCLOAK_URL || 'http://keycloak.localhost:8080';
const PROFILE = __ENV.PROFILE || 'plateau';
const RATE = Number(__ENV.RATE || 20); // orders per second on the plateau

// Arrival-rate executors keep the request rate fixed when the service slows down,
// so the latency is not hidden by fewer requests (coordinated omission).
const profiles = {
  smoke: { startRate: 1, stages: [{ target: 1, duration: '30s' }] },
  plateau: {
    startRate: 1,
    stages: [
      { target: RATE, duration: '2m' },
      { target: RATE, duration: '8m' },
      { target: 0, duration: '30s' },
    ],
  },
  // Grows until the service breaks: shows the ceiling and which alert fires first.
  stress: {
    startRate: 10,
    stages: [
      { target: RATE * 10, duration: '12m' },
      { target: 0, duration: '30s' },
    ],
  },
};

export const options = {
  scenarios: {
    orders: {
      executor: 'ramping-arrival-rate',
      timeUnit: '1s',
      preAllocatedVUs: 50,
      maxVUs: 500,
      ...profiles[PROFILE],
    },
  },
  // The service SLOs (observability/slo/): 99.9 % non-5xx, 95 % of requests under 500 ms.
  // Here they act as pass/fail criteria for the client view; setup() traffic is excluded.
  thresholds: {
    'http_req_failed{scenario:orders}': ['rate<0.001'],
    'http_req_duration{scenario:orders}': ['p(95)<500'],
    'checks{scenario:orders}': ['rate>0.999'],
  },
  summaryTrendStats: ['avg', 'med', 'p(95)', 'p(99)', 'max'],
};

// One token for the whole run; the realm gives it 15 minutes (accessTokenLifespan 900).
export function setup() {
  const res = http.post(`${KEYCLOAK}/realms/blueprints/protocol/openid-connect/token`, {
    grant_type: 'password', client_id: 'blueprints-cli', username: 'alice', password: 'alice',
  });
  if (res.status !== 200) exec.test.abort(`token request failed: ${res.status}`);
  return { token: res.json('access_token') };
}

export default function (data) {
  const headers = { Authorization: `Bearer ${data.token}`, 'Content-Type': 'application/json' };
  const n = exec.scenario.iterationInTest;

  const placed = http.post(`${BASE}/orders`, JSON.stringify({
    customerId: `k6-${PROFILE}-${n % 1000}`,
    lines: [{ productCode: 'SKU-1', quantity: 1 + (n % 3), unitPrice: 10 }],
  }), { headers, tags: { name: 'POST /orders' } });
  if (!check(placed, { 'placed 201': (r) => r.status === 201 })) return;

  const id = placed.json('id');
  const read = http.get(`${BASE}/orders/${id}`, { headers, tags: { name: 'GET /orders/{id}' } });
  check(read, { 'read 200': (r) => r.status === 200 });

  if (n % 10 === 0) {
    const cancelled = http.post(`${BASE}/orders/${id}/cancel`, JSON.stringify({ reason: 'k6' }),
      { headers, tags: { name: 'POST /orders/{id}/cancel' } });
    check(cancelled, { 'cancelled 200': (r) => r.status === 200 });
  }
}
