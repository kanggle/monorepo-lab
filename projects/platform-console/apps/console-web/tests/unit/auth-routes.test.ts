import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * GAP OIDC route-handler tests (PKCE login + callback + refresh).
 * `next/headers` cookies() and getServerEnv() are mocked so handlers run
 * directly under jsdom.
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

// vi.mock is hoisted above module-level consts; ENV must be hoisted too,
// otherwise the factory hits a TDZ ReferenceError on ENV. vi.hoisted()
// also returns ENV for use in the test bodies below.
const { ENV } = vi.hoisted(() => ({
  ENV: {
    OIDC_ISSUER_URL: 'http://gap.local',
    OIDC_CLIENT_ID: 'platform-console-web',
    OIDC_REDIRECT_URI: 'http://console.local/api/auth/callback',
    OIDC_SCOPE: 'openid profile email tenant.read',
    CONSOLE_REGISTRY_URL: 'http://gap.local/api/admin/console/registry',
    REGISTRY_TIMEOUT_MS: 5000,
    CONSOLE_TOKEN_EXCHANGE_URL: 'http://gap.local/api/admin/auth/token-exchange',
    TOKEN_EXCHANGE_TIMEOUT_MS: 5000,
    LOG_LEVEL: 'info' as const,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

import { GET as loginGET } from '@/app/api/auth/login/route';
import { GET as callbackGET } from '@/app/api/auth/callback/route';
import { POST as refreshPOST } from '@/app/api/auth/refresh/route';
import {
  ACCESS_COOKIE,
  REFRESH_COOKIE,
  OPERATOR_COOKIE,
  PKCE_VERIFIER_COOKIE,
  OAUTH_STATE_COOKIE,
} from '@/shared/lib/session';

/**
 * Helper: a fetch mock that returns the GAP OIDC token response for the
 * `/oauth2/token` call and the operator-token-exchange 200 for the
 * `/api/admin/auth/token-exchange` call (the callback + refresh routes now
 * perform BOTH — § 2.6 / ADR-MONO-014 wiring).
 */
function gapThenExchangeFetch(
  gapBody: Record<string, unknown>,
  exchangeBody: Record<string, unknown> = {
    accessToken: 'op.jwt',
    expiresIn: 900,
    tokenType: 'admin',
  },
) {
  return vi.fn((url: string) => {
    if (String(url).includes('/api/admin/auth/token-exchange')) {
      return Promise.resolve(
        new Response(JSON.stringify(exchangeBody), {
          status: 200,
          headers: { 'Content-Type': 'application/json' },
        }),
      );
    }
    return Promise.resolve(
      new Response(JSON.stringify(gapBody), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    );
  });
}

beforeEach(() => {
  cookieJar.clear();
  cookieDeletes.length = 0;
  vi.unstubAllGlobals();
});

describe('GET /api/auth/login (PKCE initiation)', () => {
  it('redirects to GAP /oauth2/authorize with PKCE S256 + state and sets transient HttpOnly cookies', async () => {
    const req = new Request('http://console.local/api/auth/login?redirect=/console');
    const res = await loginGET(req);

    expect(res.status).toBe(307);
    const loc = new URL(res.headers.get('location')!);
    expect(loc.origin + loc.pathname).toBe('http://gap.local/oauth2/authorize');
    expect(loc.searchParams.get('response_type')).toBe('code');
    expect(loc.searchParams.get('client_id')).toBe('platform-console-web');
    expect(loc.searchParams.get('redirect_uri')).toBe(ENV.OIDC_REDIRECT_URI);
    expect(loc.searchParams.get('scope')).toBe(ENV.OIDC_SCOPE);
    expect(loc.searchParams.get('code_challenge_method')).toBe('S256');
    expect(loc.searchParams.get('code_challenge')).toBeTruthy();
    expect(loc.searchParams.get('state')).toBeTruthy();

    const verifier = cookieJar.get(PKCE_VERIFIER_COOKIE);
    const state = cookieJar.get(OAUTH_STATE_COOKIE);
    expect(verifier?.opts).toMatchObject({
      httpOnly: true,
      secure: true,
      sameSite: 'strict',
    });
    expect(state?.value).toContain('|/console');
  });

  it('sanitises an off-site redirect target to "/"', async () => {
    const req = new Request(
      'http://console.local/api/auth/login?redirect=https://evil.example',
    );
    await loginGET(req);
    expect(cookieJar.get(OAUTH_STATE_COOKIE)?.value).toMatch(/\|\/$/);
  });
});

describe('GET /api/auth/callback (token exchange)', () => {
  it('on state mismatch, redirects to /login (safe re-login, no token set)', async () => {
    cookieJar.set(PKCE_VERIFIER_COOKIE, { value: 'v', opts: {} });
    cookieJar.set(OAUTH_STATE_COOKIE, { value: 'expected|/console', opts: {} });

    const req = new Request(
      'http://console.local/api/auth/callback?code=abc&state=WRONG',
    );
    const res = await callbackGET(req);
    expect(res.status).toBe(307);
    expect(res.headers.get('location')).toContain('/login?error=state_mismatch');
    expect(cookieJar.has(ACCESS_COOKIE)).toBe(false);
    // transient cookies cleared regardless
    expect(cookieDeletes).toContain(PKCE_VERIFIER_COOKIE);
    expect(cookieDeletes).toContain(OAUTH_STATE_COOKIE);
  });

  it('exchanges code+verifier as a public client and sets HttpOnly token cookies (+ operator cookie via the § 2.6 exchange)', async () => {
    cookieJar.set(PKCE_VERIFIER_COOKIE, { value: 'verifier-xyz', opts: {} });
    cookieJar.set(OAUTH_STATE_COOKIE, { value: 's1|/console', opts: {} });

    const fetchMock = gapThenExchangeFetch({
      access_token: 'acc.jwt',
      token_type: 'Bearer',
      expires_in: 1800,
      refresh_token: 'ref.jwt',
      scope: 'openid profile email tenant.read',
    });
    vi.stubGlobal('fetch', fetchMock);

    const req = new Request(
      'http://console.local/api/auth/callback?code=AUTHCODE&state=s1',
    );
    const res = await callbackGET(req);

    expect(res.status).toBe(307);
    expect(res.headers.get('location')).toBe('http://console.local/console');

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('http://gap.local/oauth2/token');
    const sent = new URLSearchParams((init as RequestInit).body as string);
    expect(sent.get('grant_type')).toBe('authorization_code');
    expect(sent.get('code')).toBe('AUTHCODE');
    expect(sent.get('code_verifier')).toBe('verifier-xyz');
    expect(sent.get('client_id')).toBe('platform-console-web');
    expect(sent.has('client_secret')).toBe(false); // public client

    const acc = cookieJar.get(ACCESS_COOKIE);
    const ref = cookieJar.get(REFRESH_COOKIE);
    expect(acc?.value).toBe('acc.jwt');
    expect(acc?.opts).toMatchObject({
      httpOnly: true,
      secure: true,
      sameSite: 'strict',
      path: '/',
      maxAge: 1800,
    });
    expect(ref?.value).toBe('ref.jwt');

    // The operator token (from the § 2.6 exchange) is set in its own
    // HttpOnly cookie with maxAge = exchange expiresIn.
    const op = cookieJar.get(OPERATOR_COOKIE);
    expect(op?.value).toBe('op.jwt');
    expect(op?.opts).toMatchObject({
      httpOnly: true,
      secure: true,
      sameSite: 'strict',
      path: '/',
      maxAge: 900,
    });
  });

  it('on token endpoint failure, redirects to /login without setting tokens', async () => {
    cookieJar.set(PKCE_VERIFIER_COOKIE, { value: 'v', opts: {} });
    cookieJar.set(OAUTH_STATE_COOKIE, { value: 's|/', opts: {} });
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ error: 'invalid_grant' }), {
          status: 400,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    );
    const req = new Request(
      'http://console.local/api/auth/callback?code=x&state=s',
    );
    const res = await callbackGET(req);
    expect(res.headers.get('location')).toContain(
      '/login?error=token_exchange_failed',
    );
    expect(cookieJar.has(ACCESS_COOKIE)).toBe(false);
  });

  it('exchange 401 → redirect not_provisioned, NO operator cookie, GAP cookies cleared (no GAP token left as an admin credential)', async () => {
    cookieJar.set(PKCE_VERIFIER_COOKIE, { value: 'v', opts: {} });
    cookieJar.set(OAUTH_STATE_COOKIE, { value: 's|/console', opts: {} });
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string) => {
        if (String(url).includes('/api/admin/auth/token-exchange')) {
          return Promise.resolve(
            new Response(JSON.stringify({ code: 'TOKEN_INVALID' }), {
              status: 401,
              headers: { 'Content-Type': 'application/json' },
            }),
          );
        }
        return Promise.resolve(
          new Response(
            JSON.stringify({
              access_token: 'gap.acc',
              token_type: 'Bearer',
              expires_in: 1800,
              refresh_token: 'gap.ref',
            }),
            { status: 200, headers: { 'Content-Type': 'application/json' } },
          ),
        );
      }),
    );
    const req = new Request(
      'http://console.local/api/auth/callback?code=x&state=s',
    );
    const res = await callbackGET(req);
    expect(res.status).toBe(307);
    expect(res.headers.get('location')).toContain(
      '/login?error=not_provisioned',
    );
    expect(cookieJar.has(OPERATOR_COOKIE)).toBe(false);
    // The GAP token must NOT be left usable as an /api/admin/** credential.
    expect(cookieJar.has(ACCESS_COOKIE)).toBe(false);
    expect(cookieDeletes).toContain(ACCESS_COOKIE);
    expect(cookieDeletes).toContain(REFRESH_COOKIE);
  });

  it('exchange unavailable (5xx) → redirect operator_exchange_unavailable, no partial authed state', async () => {
    cookieJar.set(PKCE_VERIFIER_COOKIE, { value: 'v', opts: {} });
    cookieJar.set(OAUTH_STATE_COOKIE, { value: 's|/console', opts: {} });
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string) => {
        if (String(url).includes('/api/admin/auth/token-exchange')) {
          return Promise.resolve(
            new Response(JSON.stringify({ code: 'DOWNSTREAM_ERROR' }), {
              status: 503,
              headers: { 'Content-Type': 'application/json' },
            }),
          );
        }
        return Promise.resolve(
          new Response(
            JSON.stringify({
              access_token: 'gap.acc',
              token_type: 'Bearer',
              expires_in: 1800,
              refresh_token: 'gap.ref',
            }),
            { status: 200, headers: { 'Content-Type': 'application/json' } },
          ),
        );
      }),
    );
    const req = new Request(
      'http://console.local/api/auth/callback?code=x&state=s',
    );
    const res = await callbackGET(req);
    expect(res.status).toBe(307);
    expect(res.headers.get('location')).toContain(
      '/login?error=operator_exchange_unavailable',
    );
    expect(cookieJar.has(OPERATOR_COOKIE)).toBe(false);
    expect(cookieJar.has(ACCESS_COOKIE)).toBe(false);
  });
});

describe('POST /api/auth/refresh (public-client rotation)', () => {
  it('401 when no refresh cookie', async () => {
    const res = await refreshPOST();
    expect(res.status).toBe(401);
    expect((await res.json()).code).toBe('TOKEN_INVALID');
  });

  it('rotates tokens on success and re-exchanges the operator token', async () => {
    cookieJar.set(REFRESH_COOKIE, { value: 'old.ref', opts: {} });
    vi.stubGlobal(
      'fetch',
      gapThenExchangeFetch(
        {
          access_token: 'new.acc',
          token_type: 'Bearer',
          expires_in: 1800,
          refresh_token: 'new.ref',
        },
        { accessToken: 'new.op', expiresIn: 600, tokenType: 'admin' },
      ),
    );
    const res = await refreshPOST();
    expect(res.status).toBe(200);
    expect(cookieJar.get(ACCESS_COOKIE)?.value).toBe('new.acc');
    expect(cookieJar.get(REFRESH_COOKIE)?.value).toBe('new.ref');
    // Re-exchange model (ADR-MONO-014 D2): operator cookie refreshed.
    expect(cookieJar.get(OPERATOR_COOKIE)?.value).toBe('new.op');
    expect(cookieJar.get(OPERATOR_COOKIE)?.opts).toMatchObject({
      maxAge: 600,
    });
  });

  it('clears all session cookies (incl. operator) and 401s when GAP rejects the refresh token', async () => {
    cookieJar.set(REFRESH_COOKIE, { value: 'reused.ref', opts: {} });
    cookieJar.set(OPERATOR_COOKIE, { value: 'stale.op', opts: {} });
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ error: 'invalid_grant' }), {
          status: 400,
        }),
      ),
    );
    const res = await refreshPOST();
    expect(res.status).toBe(401);
    expect(cookieDeletes).toContain(ACCESS_COOKIE);
    expect(cookieDeletes).toContain(REFRESH_COOKIE);
    expect(cookieDeletes).toContain(OPERATOR_COOKIE);
  });

  it('drops the whole session when GAP refresh succeeds but the re-exchange 401s (operator deactivated since login)', async () => {
    cookieJar.set(REFRESH_COOKIE, { value: 'old.ref', opts: {} });
    cookieJar.set(OPERATOR_COOKIE, { value: 'stale.op', opts: {} });
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string) => {
        if (String(url).includes('/api/admin/auth/token-exchange')) {
          return Promise.resolve(
            new Response(JSON.stringify({ code: 'TOKEN_INVALID' }), {
              status: 401,
              headers: { 'Content-Type': 'application/json' },
            }),
          );
        }
        return Promise.resolve(
          new Response(
            JSON.stringify({
              access_token: 'new.acc',
              token_type: 'Bearer',
              expires_in: 1800,
              refresh_token: 'new.ref',
            }),
            { status: 200, headers: { 'Content-Type': 'application/json' } },
          ),
        );
      }),
    );
    const res = await refreshPOST();
    expect(res.status).toBe(401);
    expect((await res.json()).code).toBe('TOKEN_INVALID');
    // No stale operator token, no GAP-token fallback on /api/admin/**.
    expect(cookieDeletes).toContain(ACCESS_COOKIE);
    expect(cookieDeletes).toContain(REFRESH_COOKIE);
    expect(cookieDeletes).toContain(OPERATOR_COOKIE);
  });
});
