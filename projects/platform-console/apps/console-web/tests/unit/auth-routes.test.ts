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
  PKCE_VERIFIER_COOKIE,
  OAUTH_STATE_COOKIE,
} from '@/shared/lib/session';

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

  it('exchanges code+verifier as a public client and sets HttpOnly token cookies', async () => {
    cookieJar.set(PKCE_VERIFIER_COOKIE, { value: 'verifier-xyz', opts: {} });
    cookieJar.set(OAUTH_STATE_COOKIE, { value: 's1|/console', opts: {} });

    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          access_token: 'acc.jwt',
          token_type: 'Bearer',
          expires_in: 1800,
          refresh_token: 'ref.jwt',
          scope: 'openid profile email tenant.read',
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    );
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
});

describe('POST /api/auth/refresh (public-client rotation)', () => {
  it('401 when no refresh cookie', async () => {
    const res = await refreshPOST();
    expect(res.status).toBe(401);
    expect((await res.json()).code).toBe('TOKEN_INVALID');
  });

  it('rotates tokens on success (reuse-refresh-tokens=false)', async () => {
    cookieJar.set(REFRESH_COOKIE, { value: 'old.ref', opts: {} });
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            access_token: 'new.acc',
            token_type: 'Bearer',
            expires_in: 1800,
            refresh_token: 'new.ref',
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } },
        ),
      ),
    );
    const res = await refreshPOST();
    expect(res.status).toBe(200);
    expect(cookieJar.get(ACCESS_COOKIE)?.value).toBe('new.acc');
    expect(cookieJar.get(REFRESH_COOKIE)?.value).toBe('new.ref');
  });

  it('clears cookies and 401s when GAP rejects the refresh token', async () => {
    cookieJar.set(REFRESH_COOKIE, { value: 'reused.ref', opts: {} });
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
  });
});
