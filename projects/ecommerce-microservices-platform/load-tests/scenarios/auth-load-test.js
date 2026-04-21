import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';
import { BASE_URL, DEFAULT_HEADERS, DEFAULT_THRESHOLDS } from '../lib/config.js';
import { randomInt } from '../lib/helpers.js';

/**
 * 인증 API 부하 테스트
 *
 * 대상 엔드포인트:
 * - POST /api/auth/signup
 * - POST /api/auth/login
 * - POST /api/auth/refresh
 * - POST /api/auth/logout
 */

const loginDuration = new Trend('auth_login_duration', true);
const refreshDuration = new Trend('auth_refresh_duration', true);
const authErrors = new Rate('auth_error_rate');

export const options = {
  scenarios: {
    auth_smoke: {
      executor: 'constant-vus',
      vus: 5,
      duration: '30s',
      tags: { test_type: 'smoke' },
      env: { SCENARIO: 'smoke' },
    },
    auth_load: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 20 },
        { duration: '1m', target: 50 },
        { duration: '30s', target: 50 },
        { duration: '30s', target: 0 },
      ],
      startTime: '35s',
      tags: { test_type: 'load' },
      env: { SCENARIO: 'load' },
    },
    auth_stress: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 50 },
        { duration: '1m', target: 100 },
        { duration: '30s', target: 100 },
        { duration: '30s', target: 0 },
      ],
      startTime: '3m30s',
      tags: { test_type: 'stress' },
      env: { SCENARIO: 'stress' },
    },
  },
  thresholds: {
    ...DEFAULT_THRESHOLDS,
    auth_login_duration: ['p(95)<400', 'p(99)<800'],
    auth_refresh_duration: ['p(95)<300', 'p(99)<600'],
    auth_error_rate: ['rate<0.01'],
    http_reqs: ['rate>20'],
  },
};

export default function () {
  const uniqueId = `${__VU}-${__ITER}-${Date.now()}`;
  const email = `loadtest-${uniqueId}@test.com`;
  const password = 'LoadTest1234!';
  const name = `User${uniqueId}`;

  // 1. 회원가입
  const signupRes = http.post(
    `${BASE_URL}/api/auth/signup`,
    JSON.stringify({ email, password, name }),
    { headers: DEFAULT_HEADERS, tags: { endpoint: 'signup' } }
  );

  const signupOk = check(signupRes, {
    'signup: status 201': (r) => r.status === 201,
  });

  if (!signupOk) {
    authErrors.add(1);
    return;
  }
  authErrors.add(0);

  sleep(randomInt(1, 3));

  // 2. 로그인
  const loginRes = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ email, password }),
    { headers: DEFAULT_HEADERS, tags: { endpoint: 'login' } }
  );

  loginDuration.add(loginRes.timings.duration);

  const loginOk = check(loginRes, {
    'login: status 200': (r) => r.status === 200,
    'login: has accessToken': (r) => JSON.parse(r.body).accessToken !== undefined,
    'login: has refreshToken': (r) => JSON.parse(r.body).refreshToken !== undefined,
  });

  if (!loginOk) {
    authErrors.add(1);
    return;
  }
  authErrors.add(0);

  const { accessToken, refreshToken } = JSON.parse(loginRes.body);

  sleep(randomInt(1, 3));

  // 3. 토큰 갱신
  const refreshRes = http.post(
    `${BASE_URL}/api/auth/refresh`,
    JSON.stringify({ refreshToken }),
    { headers: DEFAULT_HEADERS, tags: { endpoint: 'refresh' } }
  );

  refreshDuration.add(refreshRes.timings.duration);

  const refreshOk = check(refreshRes, {
    'refresh: status 200': (r) => r.status === 200,
    'refresh: has new accessToken': (r) => JSON.parse(r.body).accessToken !== undefined,
  });

  if (!refreshOk) {
    authErrors.add(1);
    return;
  }
  authErrors.add(0);

  const newTokens = JSON.parse(refreshRes.body);

  sleep(randomInt(1, 2));

  // 4. 로그아웃
  const logoutRes = http.post(
    `${BASE_URL}/api/auth/logout`,
    JSON.stringify({ refreshToken: newTokens.refreshToken }),
    {
      headers: {
        ...DEFAULT_HEADERS,
        Authorization: `Bearer ${newTokens.accessToken}`,
      },
      tags: { endpoint: 'logout' },
    }
  );

  check(logoutRes, {
    'logout: status 204': (r) => r.status === 204,
  });

  sleep(randomInt(1, 3));
}
