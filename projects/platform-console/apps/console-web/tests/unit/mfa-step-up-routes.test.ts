import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * TASK-MONO-771 S3 — the console's second-factor branches
 * (console-integration-contract § 2.6 / § 2.6.1 / § 2.7).
 *
 *   - callback: exchange `403 MFA_REQUIRED` → `/api/auth/step-up` (never
 *     `/onboarding`); 🔴 only the `401` means «not an operator»;
 *   - `GET /api/auth/step-up`: a fresh authorize with `acr_values=mfa`, marked;
 *   - loop bound: a callback for the marked request that is still refused →
 *     `/login?error=mfa_required`;
 *   - refresh (GET + POST): re-exchange `403 MFA_REQUIRED` → step-up / `403`;
 *   - `/api/tenant`: assume `invalid_grant` + `insufficient_user_authentication`
 *     (whole-value) → `403 MFA_REQUIRED`, before `denied`;
 *   - the default-tenant assume at login, refused the same way, is NOT fatal and
 *     triggers NO step-up.
 *
 * Each 🔴 cell has a 🔵 control on the neighbouring branch.
 */

const cookieJar = new Map<string, { value: string; opts: Record<string, unknown> }>();
const cookieDeletes: string[] = [];
const cookiesMock = {
  get: (name: string) => {
    const e = cookieJar.get(name);
    return e ? { value: e.value } : undefined;
  },
  set: (name: string, value: string, opts: Record<string, unknown>) => {
    cookieJar.set(name, { value, opts });
  },
  delete: (name: string) => {
    cookieJar.delete(name);
    cookieDeletes.push(name);
  },
};
vi.mock('next/headers', () => ({ cookies: async () => cookiesMock }));

const { ENV } = vi.hoisted(() => ({
  ENV: {
    OIDC_ISSUER_URL: 'http://iam.local',
    OIDC_CLIENT_ID: 'platform-console-web',
    OIDC_REDIRECT_URI: 'http://console.local/api/auth/callback',
    OIDC_SCOPE: 'openid profile email tenant.read',
    CONSOLE_REGISTRY_URL: 'http://iam.local/api/admin/console/registry',
    REGISTRY_TIMEOUT_MS: 50,
    CONSOLE_TOKEN_EXCHANGE_URL: 'http://iam.local/api/admin/auth/token-exchange',
    TOKEN_EXCHANGE_TIMEOUT_MS: 50,
    LOG_LEVEL: 'info' as const,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
  publicOrigin: (e: { CONSOLE_PUBLIC_ORIGIN?: string; NEXT_PUBLIC_APP_URL: string }) =>
    e.CONSOLE_PUBLIC_ORIGIN ?? e.NEXT_PUBLIC_APP_URL,
}));

vi.mock('@/shared/lib/session-refresh', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/shared/lib/session-refresh')>();
  return { ...actual, REFRESH_RACE_GRACE_MS: 10 };
});

// The registry read (default tenant at login; switch allow-check) is not under
// test here — mocked so the token-exchange `fetch` stubs stay unambiguous.
const fetchRegistryMock = vi.fn();
vi.mock('@/shared/api/registry-client', () => ({
  fetchRegistry: () => fetchRegistryMock(),
}));

import { GET as loginGET } from '@/app/api/auth/login/route';
import { GET as stepUpGET } from '@/app/api/auth/step-up/route';
import { GET as callbackGET } from '@/app/api/auth/callback/route';
import { GET as refreshGET, POST as refreshPOST } from '@/app/api/auth/refresh/route';
import { POST as tenantPOST } from '@/app/api/tenant/route';
import {
  ACCESS_COOKIE,
  REFRESH_COOKIE,
  OPERATOR_COOKIE,
  TENANT_COOKIE,
  ASSUMED_TOKEN_COOKIE,
  PKCE_VERIFIER_COOKIE,
  OAUTH_STATE_COOKIE,
  STEP_UP_MARKER_COOKIE,
} from '@/shared/lib/session';

const EXCHANGE_URL = 'http://iam.local/api/admin/auth/token-exchange';

function json(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

const MFA_403 = () => json({ code: 'MFA_REQUIRED', message: 'second factor required' }, 403);
const NOT_OPERATOR_401 = () => json({ code: 'TOKEN_INVALID' }, 401);
const ASSUME_MFA_400 = () =>
  json({ error: 'invalid_grant', error_description: 'insufficient_user_authentication' }, 400);

/**
 * `fetch` stub: the operator exchange answers `exchange()`; the SAS
 * `/oauth2/token` answers the token-exchange (assume) grant with `assume()` and
 * every other grant (authorization_code / refresh_token) with a token pair.
 */
function stubFetch(opts: {
  exchange: () => Response;
  assume?: () => Response;
}) {
  const fetchMock = vi.fn((url: string, init?: RequestInit) => {
    if (String(url) === EXCHANGE_URL) return Promise.resolve(opts.exchange());
    const body = String(init?.body ?? '');
    if (body.includes('grant-type%3Atoken-exchange')) {
      return Promise.resolve(
        (opts.assume ?? (() => json({ access_token: 'assumed', token_type: 'Bearer', expires_in: 600 }, 200)))(),
      );
    }
    return Promise.resolve(
      json(
        { access_token: 'iam.acc', token_type: 'Bearer', expires_in: 1800, refresh_token: 'iam.ref' },
        200,
      ),
    );
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function seedCallback(state: string, postLoginPath = '/iam/accounts') {
  cookieJar.set(PKCE_VERIFIER_COOKIE, { value: 'verifier', opts: {} });
  cookieJar.set(OAUTH_STATE_COOKIE, { value: `${state}|${postLoginPath}`, opts: {} });
}

function callback(state: string, extra = '') {
  return callbackGET(
    new Request(`http://console.local/api/auth/callback?code=CODE&state=${state}${extra}`),
  );
}

beforeEach(() => {
  cookieJar.clear();
  cookieDeletes.length = 0;
  fetchRegistryMock.mockReset();
  fetchRegistryMock.mockRejectedValue(new Error('registry not under test'));
  vi.unstubAllGlobals();
});

// ─────────────────────────────────────────────────────────────────────────────
describe('GET /api/auth/step-up — a fresh authorize with acr_values=mfa (§ 2.6)', () => {
  it('🔴 redirects to /oauth2/authorize exactly as login does, plus acr_values=mfa, and marks the request', async () => {
    const res = await stepUpGET(
      new Request('http://console.local/api/auth/step-up?redirect=/iam/accounts'),
    );
    expect(res.status).toBe(307);
    expect(res.headers.get('Cache-Control')).toBe('no-store');
    const loc = new URL(res.headers.get('location')!);
    expect(loc.origin + loc.pathname).toBe('http://iam.local/oauth2/authorize');
    expect(loc.searchParams.get('acr_values')).toBe('mfa');
    expect(loc.searchParams.get('response_type')).toBe('code');
    expect(loc.searchParams.get('client_id')).toBe('platform-console-web');
    expect(loc.searchParams.get('redirect_uri')).toBe(ENV.OIDC_REDIRECT_URI);
    expect(loc.searchParams.get('code_challenge_method')).toBe('S256');
    expect(loc.searchParams.get('code_challenge')).toBeTruthy();
    const state = loc.searchParams.get('state')!;
    expect(state).toBeTruthy();

    expect(cookieJar.get(PKCE_VERIFIER_COOKIE)?.value).toBeTruthy();
    expect(cookieJar.get(OAUTH_STATE_COOKIE)?.value).toBe(`${state}|/iam/accounts`);
    // The loop-bound mark is bound to THIS request's state, HttpOnly.
    expect(cookieJar.get(STEP_UP_MARKER_COOKIE)?.value).toBe(state);
    expect(cookieJar.get(STEP_UP_MARKER_COOKIE)?.opts).toMatchObject({ httpOnly: true });
  });

  it('🔴 keeps the session (§ 2.6 «the IAM cookies are kept») — unlike login', async () => {
    cookieJar.set(ACCESS_COOKIE, { value: 'iam.acc', opts: {} });
    cookieJar.set(REFRESH_COOKIE, { value: 'iam.ref', opts: {} });
    cookieJar.set(OPERATOR_COOKIE, { value: 'op', opts: {} });
    await stepUpGET(new Request('http://console.local/api/auth/step-up'));
    expect(cookieDeletes).not.toContain(ACCESS_COOKIE);
    expect(cookieDeletes).not.toContain(REFRESH_COOKIE);
    expect(cookieDeletes).not.toContain(OPERATOR_COOKIE);
  });

  it.each([
    ['https://evil.example', '/'],
    ['//evil.example', '/'],
    ['/login?error=x', '/'],
    ['/api/auth/step-up', '/'],
    [null, '/'],
  ])('🔴 consume-side sanitises redirect=%s → %s', async (raw, expected) => {
    const q = raw === null ? '' : `?redirect=${encodeURIComponent(raw)}`;
    await stepUpGET(new Request(`http://console.local/api/auth/step-up${q}`));
    expect(cookieJar.get(OAUTH_STATE_COOKIE)?.value.split('|')[1]).toBe(expected);
  });

  it('🔵 control — login carries NO acr_values and clears a stale step-up mark', async () => {
    cookieJar.set(STEP_UP_MARKER_COOKIE, { value: 'stale-state', opts: {} });
    const res = await loginGET(new Request('http://console.local/api/auth/login?redirect=/x'));
    const loc = new URL(res.headers.get('location')!);
    expect(loc.searchParams.has('acr_values')).toBe(false);
    expect(cookieJar.has(STEP_UP_MARKER_COOKIE)).toBe(false);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
describe('GET /api/auth/callback — exchange 403 MFA_REQUIRED (§ 2.6)', () => {
  it('🔴 403 MFA_REQUIRED → /api/auth/step-up?redirect=<post-login path>, never /onboarding; IAM cookies KEPT, no operator cookie', async () => {
    seedCallback('s1', '/iam/accounts');
    cookieJar.set(TENANT_COOKIE, { value: 'stale', opts: {} });
    cookieJar.set(ASSUMED_TOKEN_COOKIE, { value: 'stale.assumed', opts: {} });
    stubFetch({ exchange: MFA_403 });

    const res = await callback('s1');
    expect(res.status).toBe(307);
    expect(res.headers.get('location')).toBe(
      `http://console.local/api/auth/step-up?redirect=${encodeURIComponent('/iam/accounts')}`,
    );
    expect(res.headers.get('location')).not.toContain('/onboarding');
    expect(cookieJar.has(OPERATOR_COOKIE)).toBe(false);
    expect(cookieJar.get(ACCESS_COOKIE)?.value).toBe('iam.acc');
    expect(cookieJar.get(REFRESH_COOKIE)?.value).toBe('iam.ref');
    expect(cookieDeletes).toContain(TENANT_COOKIE);
    expect(cookieDeletes).toContain(ASSUMED_TOKEN_COOKIE);
  });

  it('🔴🔴 «401 만 운영자 아님» — exchange 401 still goes to /onboarding (control on the neighbouring branch)', async () => {
    seedCallback('s1');
    stubFetch({ exchange: NOT_OPERATOR_401 });
    const res = await callback('s1');
    expect(res.headers.get('location')).toBe('http://console.local/onboarding');
  });

  it('🔵 control — a 401 for a STEPPED-UP request is still «not an operator» (the mark changes nothing there)', async () => {
    seedCallback('s1');
    cookieJar.set(STEP_UP_MARKER_COOKIE, { value: 's1', opts: {} });
    stubFetch({ exchange: NOT_OPERATOR_401 });
    const res = await callback('s1');
    expect(res.headers.get('location')).toBe('http://console.local/onboarding');
  });

  it('🔵 control — a 403 with another code is not a step-up (session-unavailable, as before)', async () => {
    seedCallback('s1');
    stubFetch({ exchange: () => json({ code: 'PERMISSION_DENIED' }, 403) });
    const res = await callback('s1');
    expect(res.headers.get('location')).toContain('/login?error=operator_exchange_unavailable');
    expect(cookieJar.has(ACCESS_COOKIE)).toBe(false);
  });

  it('🔵 control — exchange network failure keeps its old destination', async () => {
    seedCallback('s1');
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string) =>
        String(url) === EXCHANGE_URL
          ? Promise.reject(new Error('ECONNREFUSED'))
          : Promise.resolve(
              json({ access_token: 'iam.acc', token_type: 'Bearer', expires_in: 1800 }, 200),
            ),
      ),
    );
    const res = await callback('s1');
    expect(res.headers.get('location')).toContain('/login?error=operator_exchange_unavailable');
  });

  it('🔵 control — another token-endpoint invalid_grant keeps token_exchange_failed', async () => {
    seedCallback('s1');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        json({ error: 'invalid_grant', error_description: 'insufficient_user_authentication' }, 400),
      ),
    );
    // Even the assume refusal's description, arriving on the CODE grant, is not
    // a step-up here — only the operator exchange's 403 is (§ 2.6).
    const res = await callback('s1');
    expect(res.headers.get('location')).toContain('/login?error=token_exchange_failed');
  });
});

// ─────────────────────────────────────────────────────────────────────────────
describe('loop bound — one automatic step-up per login (§ 2.6)', () => {
  it('🔴🔴 step-up → callback still 403 → /login?error=mfa_required (not a second step-up)', async () => {
    // 1) the step-up route starts and marks the request.
    const up = await stepUpGET(
      new Request('http://console.local/api/auth/step-up?redirect=/iam/accounts'),
    );
    const state = new URL(up.headers.get('location')!).searchParams.get('state')!;

    // 2) IAM comes back, the exchange is STILL refused.
    stubFetch({ exchange: MFA_403 });
    const res = await callback(state);
    expect(res.headers.get('location')).toBe('http://console.local/login?error=mfa_required');
    expect(cookieJar.has(OPERATOR_COOKIE)).toBe(false);
    // The mark is single-use — gone after this callback.
    expect(cookieJar.has(STEP_UP_MARKER_COOKIE)).toBe(false);
  });

  it('🔵 control — a mark bound to ANOTHER state (an abandoned step-up) does not end the flow: step up', async () => {
    seedCallback('fresh');
    cookieJar.set(STEP_UP_MARKER_COOKIE, { value: 'abandoned', opts: {} });
    stubFetch({ exchange: MFA_403 });
    const res = await callback('fresh');
    expect(res.headers.get('location')).toContain('/api/auth/step-up');
  });

  it('🔵 control — step-up then success: the operator cookie is set and the target reached', async () => {
    const up = await stepUpGET(
      new Request('http://console.local/api/auth/step-up?redirect=/iam/accounts'),
    );
    const state = new URL(up.headers.get('location')!).searchParams.get('state')!;
    stubFetch({ exchange: () => json({ accessToken: 'op', expiresIn: 900, tokenType: 'admin' }, 200) });
    const res = await callback(state);
    expect(res.headers.get('location')).toBe('http://console.local/iam/accounts');
    expect(cookieJar.get(OPERATOR_COOKIE)?.value).toBe('op');
  });

  it('🔴 IAM «취소» (access_denied + mfa_cancelled) lands on the same /login?error=mfa_required', async () => {
    seedCallback('s1');
    const res = await callback('s1', '&error=access_denied&error_description=mfa_cancelled');
    expect(res.headers.get('location')).toBe('http://console.local/login?error=mfa_required');
  });

  it('🔵 control — any other access_denied stays provider_error', async () => {
    seedCallback('s1');
    const res = await callback('s1', '&error=access_denied&error_description=user_denied');
    expect(res.headers.get('location')).toContain('/login?error=provider_error');
  });
});

// ─────────────────────────────────────────────────────────────────────────────
describe('default active tenant at login — mfa_required refusal (§ 2.7)', () => {
  it('🔴 the default-tenant assume refused with insufficient_user_authentication is NOT fatal and starts NO step-up', async () => {
    seedCallback('s1', '/dashboards/overview');
    fetchRegistryMock.mockResolvedValue({
      products: [{ productKey: 'wms', available: true, tenants: ['acme-corp'] }],
    });
    const fetchMock = stubFetch({
      exchange: () => json({ accessToken: 'op', expiresIn: 900, tokenType: 'admin' }, 200),
      assume: ASSUME_MFA_400,
    });

    const res = await callback('s1');
    // The assume WAS attempted (the refusal is real, not skipped)…
    expect(
      fetchMock.mock.calls.some(([, init]) =>
        String((init as RequestInit | undefined)?.body ?? '').includes('audience=acme-corp'),
      ),
    ).toBe(true);
    // …and the login lands where it was going, with no active tenant.
    expect(res.headers.get('location')).toBe('http://console.local/dashboards/overview');
    expect(cookieJar.get(OPERATOR_COOKIE)?.value).toBe('op');
    expect(cookieJar.has(TENANT_COOKIE)).toBe(false);
    expect(cookieJar.has(ASSUMED_TOKEN_COOKIE)).toBe(false);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
describe('refresh re-exchange 403 MFA_REQUIRED (§ 2.6.1)', () => {
  it('🔴 GET → /api/auth/step-up?redirect=<target>; operator dropped, rotated IAM cookies kept', async () => {
    cookieJar.set(REFRESH_COOKIE, { value: 'old.ref', opts: {} });
    cookieJar.set(OPERATOR_COOKIE, { value: 'stale.op', opts: {} });
    stubFetch({ exchange: MFA_403 });

    const res = await refreshGET(
      new Request('http://console.local/api/auth/refresh?redirect=/wms'),
    );
    expect(res.headers.get('location')).toBe(
      `http://console.local/api/auth/step-up?redirect=${encodeURIComponent('/wms')}`,
    );
    expect(cookieDeletes).toContain(OPERATOR_COOKIE);
    expect(cookieJar.get(ACCESS_COOKIE)?.value).toBe('iam.acc');
    expect(cookieJar.get(REFRESH_COOKIE)?.value).toBe('iam.ref');
    expect(cookieDeletes).not.toContain(ACCESS_COOKIE);
  });

  it('🔵 control — GET re-exchange 401 still goes to /onboarding', async () => {
    cookieJar.set(REFRESH_COOKIE, { value: 'old.ref', opts: {} });
    stubFetch({ exchange: NOT_OPERATOR_401 });
    const res = await refreshGET(
      new Request('http://console.local/api/auth/refresh?redirect=/wms'),
    );
    expect(res.headers.get('location')).toBe('http://console.local/onboarding');
  });

  it('🔴 POST → 403 { code: MFA_REQUIRED }; IAM cookies not cleared', async () => {
    cookieJar.set(REFRESH_COOKIE, { value: 'old.ref', opts: {} });
    cookieJar.set(OPERATOR_COOKIE, { value: 'stale.op', opts: {} });
    stubFetch({ exchange: MFA_403 });

    const res = await refreshPOST(
      new Request('http://console.local/api/auth/refresh', { method: 'POST' }),
    );
    expect(res.status).toBe(403);
    expect((await res.json()).code).toBe('MFA_REQUIRED');
    expect(cookieDeletes).toContain(OPERATOR_COOKIE);
    expect(cookieDeletes).not.toContain(ACCESS_COOKIE);
    expect(cookieDeletes).not.toContain(REFRESH_COOKIE);
  });

  it('🔵 control — POST re-exchange 401 keeps its 401 + whole-session clear', async () => {
    cookieJar.set(REFRESH_COOKIE, { value: 'old.ref', opts: {} });
    stubFetch({ exchange: NOT_OPERATOR_401 });
    const res = await refreshPOST(
      new Request('http://console.local/api/auth/refresh', { method: 'POST' }),
    );
    expect(res.status).toBe(401);
    expect(cookieDeletes).toContain(ACCESS_COOKIE);
  });

  it('🔵 control — a re-ASSUME refused with insufficient_user_authentication only drops the tenant pair (refresh still ok)', async () => {
    cookieJar.set(REFRESH_COOKIE, { value: 'old.ref', opts: {} });
    cookieJar.set(TENANT_COOKIE, { value: 'acme-corp', opts: {} });
    stubFetch({
      exchange: () => json({ accessToken: 'op', expiresIn: 900, tokenType: 'admin' }, 200),
      assume: ASSUME_MFA_400,
    });
    const res = await refreshPOST(
      new Request('http://console.local/api/auth/refresh', { method: 'POST' }),
    );
    expect(res.status).toBe(200);
    expect(cookieDeletes).toContain(TENANT_COOKIE);
    expect(cookieDeletes).toContain(ASSUMED_TOKEN_COOKIE);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
describe('POST /api/tenant — assume refused for a second factor (§ 2.7)', () => {
  function switchTo(tenant: string) {
    return tenantPOST(
      new Request('http://console.local/api/tenant', {
        method: 'POST',
        body: JSON.stringify({ tenant }),
      }),
    );
  }

  beforeEach(() => {
    cookieJar.set(ACCESS_COOKIE, { value: 'BASE', opts: {} });
    cookieJar.set(TENANT_COOKIE, { value: 'acme-corp', opts: {} });
    cookieJar.set(ASSUMED_TOKEN_COOKIE, { value: 'OLD-ASSUMED', opts: {} });
    fetchRegistryMock.mockResolvedValue({
      products: [{ productKey: 'wms', available: true, tenants: ['acme-corp', 'globex-corp'] }],
    });
  });

  it('🔴 invalid_grant + insufficient_user_authentication → 403 MFA_REQUIRED, NO cookie change', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ASSUME_MFA_400()));
    const res = await switchTo('globex-corp');
    expect(res.status).toBe(403);
    expect((await res.json()).code).toBe('MFA_REQUIRED');
    expect(cookieJar.get(TENANT_COOKIE)?.value).toBe('acme-corp');
    expect(cookieJar.get(ASSUMED_TOKEN_COOKIE)?.value).toBe('OLD-ASSUMED');
    expect(cookieDeletes).toEqual([]);
  });

  it.each([
    ['another description', 'tenant not assigned'],
    ['case-different', 'Insufficient_User_Authentication'],
    ['substring', 'insufficient_user_authentication: tenant policy'],
    ['missing description', undefined],
  ])('🔵 control — invalid_grant with %s stays TENANT_FORBIDDEN (whole-value equality)', async (_l, desc) => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(json({ error: 'invalid_grant', error_description: desc }, 400)),
    );
    const res = await switchTo('globex-corp');
    expect(res.status).toBe(403);
    expect((await res.json()).code).toBe('TENANT_FORBIDDEN');
  });

  it('🔵 control — the description on a NON-invalid_grant error is not mfa_required (invalid_request → 422)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        json({ error: 'invalid_request', error_description: 'insufficient_user_authentication' }, 400),
      ),
    );
    const res = await switchTo('globex-corp');
    expect(res.status).toBe(422);
  });

  it('🔵 control — network failure stays 503', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('ECONNRESET')));
    const res = await switchTo('globex-corp');
    expect(res.status).toBe(503);
  });
});
