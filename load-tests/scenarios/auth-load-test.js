import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL, DEFAULT_HEADERS, authHeaders, DEFAULT_THRESHOLDS } from '../lib/config.js';
import { setupTestUser, checkStatus } from '../lib/helpers.js';

/**
 * Auth load test — login -> refresh -> logout loop
 *
 * Stages:
 *   - ramp-up to 20 VUs over 30s
 *   - hold 20 VUs for 1m
 *   - ramp-down over 15s
 *
 * Validates throughput and error thresholds during a realistic auth loop.
 */
export const options = {
  stages: [
    { duration: '30s', target: 20 },
    { duration: '1m', target: 20 },
    { duration: '15s', target: 0 },
  ],
  thresholds: DEFAULT_THRESHOLDS,
};

export function setup() {
  const users = [];
  for (let i = 0; i < 10; i++) {
    const u = setupTestUser(i);
    if (u) users.push(u);
  }
  if (users.length === 0) {
    throw new Error('setup: could not create any test user');
  }
  return { users };
}

export default function (data) {
  const user = data.users[Math.floor(Math.random() * data.users.length)];

  // Login
  const loginRes = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ email: user.email, password: user.password }),
    { headers: DEFAULT_HEADERS, tags: { endpoint: 'login' } }
  );
  checkStatus(loginRes, 200, 'login');
  if (loginRes.status !== 200) {
    sleep(1);
    return;
  }

  const body = JSON.parse(loginRes.body);
  const accessToken = body.accessToken;
  const refreshToken = body.refreshToken;

  sleep(1);

  // Refresh
  const refreshRes = http.post(
    `${BASE_URL}/api/auth/refresh`,
    JSON.stringify({ refreshToken }),
    { headers: DEFAULT_HEADERS, tags: { endpoint: 'refresh' } }
  );
  check(refreshRes, { 'refresh 200': (r) => r.status === 200 });

  sleep(1);

  // Logout
  const logoutRes = http.post(
    `${BASE_URL}/api/auth/logout`,
    null,
    { headers: authHeaders(accessToken), tags: { endpoint: 'logout' } }
  );
  check(logoutRes, { 'logout 2xx': (r) => r.status >= 200 && r.status < 300 });

  sleep(1);
}
