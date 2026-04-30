import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, DEFAULT_HEADERS } from '../lib/config.js';

/**
 * Signup load test — target RPS using constant-arrival-rate executor.
 *
 * Target: 10 RPS for 2 minutes.
 * Validates signup latency p95 < 1s and error rate < 2%.
 */
export const options = {
  scenarios: {
    signup: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RPS || 10),
      timeUnit: '1s',
      duration: '2m',
      preAllocatedVUs: 20,
      maxVUs: 50,
    },
  },
  thresholds: {
    'http_req_duration{endpoint:signup}': ['p(95)<1000', 'p(99)<2000'],
    'http_req_failed{endpoint:signup}': ['rate<0.02'],
  },
};

export default function () {
  const unique = `${__VU}-${__ITER}-${Date.now()}`;
  const payload = JSON.stringify({
    email: `signup-${unique}@loadtest.local`,
    password: 'LoadTest1234!',
    name: `SignupUser${unique}`,
  });

  const res = http.post(`${BASE_URL}/api/accounts/signup`, payload, {
    headers: DEFAULT_HEADERS,
    tags: { endpoint: 'signup' },
  });

  check(res, {
    'signup 201': (r) => r.status === 201,
  });
}
