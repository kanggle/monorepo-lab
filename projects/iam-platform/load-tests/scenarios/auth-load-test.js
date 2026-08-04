import http from 'k6/http';
import { check, sleep } from 'k6';
import encoding from 'k6/encoding';
import { BASE_URL, DEFAULT_THRESHOLDS } from '../lib/config.js';
import { issueClientCredentialsToken, checkStatus } from '../lib/helpers.js';

/**
 * Auth load test — OIDC token endpoint loop: token -> introspect -> revoke
 *
 * TASK-BE-398: this scenario used to drive the legacy custom-JWT loop
 * (`POST /api/auth/login` -> `/api/auth/refresh` -> `/api/auth/logout`). The login
 * and logout legs were removed at the ADR-001 D2-b sunset (2026-08-01); interactive
 * login is now the browser OIDC Authorization Code + PKCE flow, which is not a
 * scriptable k6 load path. The loop therefore exercises the machine-driveable OIDC
 * surface at the same endpoint the fleet's hot path uses.
 *
 * Requires client credentials in the environment:
 *   k6 run -e OIDC_CLIENT_ID=... -e OIDC_CLIENT_SECRET=... scenarios/auth-load-test.js
 *
 * Stages:
 *   - ramp-up to 20 VUs over 30s
 *   - hold 20 VUs for 1m
 *   - ramp-down over 15s
 *
 * Validates throughput and error thresholds during a realistic token loop.
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
  // Fail fast on a misconfigured run rather than reporting a 100% error rate.
  const probe = issueClientCredentialsToken({ endpoint: 'token' });
  if (!probe) {
    throw new Error(
      'setup: could not issue a client_credentials token — set OIDC_CLIENT_ID / OIDC_CLIENT_SECRET'
    );
  }
  return {};
}

export default function () {
  // 1. Token issuance (POST /oauth2/token, grant_type=client_credentials)
  const issued = issueClientCredentialsToken({ endpoint: 'token' });
  if (!issued) {
    sleep(1);
    return;
  }
  const accessToken = issued.accessToken;

  sleep(1);

  // 2. Introspection (RFC 7662) — the stateful validation path.
  const introspectRes = http.post(
    `${BASE_URL}/oauth2/introspect`,
    { token: accessToken },
    {
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth()}`,
      },
      tags: { endpoint: 'introspect' },
    }
  );
  checkStatus(introspectRes, 200, 'introspect');

  sleep(1);

  // 3. Revocation (RFC 7009) — closes the loop so tokens do not accumulate.
  const revokeRes = http.post(
    `${BASE_URL}/oauth2/revoke`,
    { token: accessToken },
    {
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${basicAuth()}`,
      },
      tags: { endpoint: 'revoke' },
    }
  );
  check(revokeRes, { 'revoke 2xx': (r) => r.status >= 200 && r.status < 300 });

  sleep(1);
}

function basicAuth() {
  return encoding.b64encode(`${__ENV.OIDC_CLIENT_ID}:${__ENV.OIDC_CLIENT_SECRET}`);
}
