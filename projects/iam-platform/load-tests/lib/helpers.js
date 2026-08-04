import http from 'k6/http';
import { check } from 'k6';
import encoding from 'k6/encoding';
import { BASE_URL } from './config.js';

/**
 * TASK-BE-398 — the legacy custom-JWT `POST /api/auth/login` (and the
 * `POST /api/auth/oauth/**` JSON social flow) were removed at their ADR-001 D2-b
 * sunset (2026-08-01). The former `setupTestUser()` helper signed a user up and
 * then logged in through that endpoint; there is no scriptable password-login
 * endpoint left — interactive login is the browser OIDC Authorization Code + PKCE
 * flow (`/oauth2/authorize` → `/login` form → `/oauth2/token`), which k6 cannot
 * drive meaningfully as a load scenario.
 *
 * The auth load scenario therefore exercises the machine-driveable OIDC surface
 * (`client_credentials` token issuance / introspection / revocation), which is the
 * remaining hot path at the token endpoint.
 */

/**
 * Issues an access token via the standard OIDC token endpoint using the
 * `client_credentials` grant.
 *
 * Credentials come from the environment so the script carries no secret:
 *   -e OIDC_CLIENT_ID=...  -e OIDC_CLIENT_SECRET=...
 *
 * @returns {{accessToken: string}|null} null when the grant is refused
 */
export function issueClientCredentialsToken(tags) {
  const clientId = __ENV.OIDC_CLIENT_ID;
  const clientSecret = __ENV.OIDC_CLIENT_SECRET;
  const scope = __ENV.OIDC_SCOPE || 'account.read';

  if (!clientId || !clientSecret) {
    console.error(
      'OIDC_CLIENT_ID / OIDC_CLIENT_SECRET must be set (k6 run -e OIDC_CLIENT_ID=... -e OIDC_CLIENT_SECRET=...)'
    );
    return null;
  }

  const res = http.post(
    `${BASE_URL}/oauth2/token`,
    { grant_type: 'client_credentials', scope },
    {
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        Authorization: `Basic ${encodeBasic(clientId, clientSecret)}`,
      },
      tags: tags || { endpoint: 'token' },
    }
  );

  if (res.status !== 200) {
    console.error(`client_credentials token request failed: ${res.status} ${res.body}`);
    return null;
  }

  return { accessToken: JSON.parse(res.body).access_token };
}

/** Basic-auth encoding for the token endpoint's client authentication. */
function encodeBasic(clientId, clientSecret) {
  return encoding.b64encode(`${clientId}:${clientSecret}`);
}

/**
 * 응답 상태 코드 체크 헬퍼
 */
export function checkStatus(res, expectedStatus, label) {
  return check(res, {
    [`${label} - status ${expectedStatus}`]: (r) => r.status === expectedStatus,
  });
}

/**
 * 임의 정수 생성 (min 이상, max 미만)
 */
export function randomInt(min, max) {
  return Math.floor(Math.random() * (max - min)) + min;
}

/**
 * 배열에서 랜덤 요소 선택
 */
export function randomChoice(arr) {
  return arr[randomInt(0, arr.length)];
}
