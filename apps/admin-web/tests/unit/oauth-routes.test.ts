import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Tests for the OAuth Route Handlers (BFF):
 *  - GET /api/auth/oauth/authorize
 *  - POST /api/auth/oauth/callback
 *
 * The Next.js `cookies()` and `getServerEnv()` helpers are mocked so the
 * handlers can be invoked directly under jsdom.
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

vi.mock('next/headers', () => ({
  cookies: async () => cookiesMock,
}));

vi.mock('@/shared/config/env', () => ({
  clientEnv: {
    NEXT_PUBLIC_API_BASE_URL: 'http://localhost:8080',
    NEXT_PUBLIC_APP_URL: 'http://localhost:3000',
    NEXT_PUBLIC_GRAFANA_ACCOUNTS_URL: 'https://example.test',
    NEXT_PUBLIC_GRAFANA_SECURITY_URL: 'https://example.test',
    NEXT_PUBLIC_GRAFANA_SYSTEM_URL: 'https://example.test',
  },
  getServerEnv: () => ({
    NEXT_PUBLIC_API_BASE_URL: 'http://localhost:8080',
    NEXT_PUBLIC_APP_URL: 'http://localhost:3000',
    NEXT_PUBLIC_GRAFANA_ACCOUNTS_URL: 'https://example.test',
    NEXT_PUBLIC_GRAFANA_SECURITY_URL: 'https://example.test',
    NEXT_PUBLIC_GRAFANA_SYSTEM_URL: 'https://example.test',
    LOG_LEVEL: 'info',
  }),
}));

import { GET as authorizeGET } from '@/app/api/auth/oauth/authorize/route';
import { POST as callbackPOST } from '@/app/api/auth/oauth/callback/route';

beforeEach(() => {
  cookieJar.clear();
  cookieDeletes.length = 0;
  vi.unstubAllGlobals();
});

describe('GET /api/auth/oauth/authorize', () => {
  it('forwards provider/redirectUri to auth-service and returns authorizationUrl + state', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          authorizationUrl: 'https://accounts.google.com/o/oauth2/v2/auth?state=abc',
          state: 'abc',
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    );
    vi.stubGlobal('fetch', fetchMock);

    const req = new Request(
      'http://localhost/api/auth/oauth/authorize?provider=google&redirectUri=http%3A%2F%2Flocalhost%3A3000%2Foauth%2Fcallback',
    );
    const res = await authorizeGET(req);
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body).toEqual({
      authorizationUrl: 'https://accounts.google.com/o/oauth2/v2/auth?state=abc',
      state: 'abc',
    });

    const upstreamUrl = fetchMock.mock.calls[0][0] as string;
    expect(upstreamUrl).toContain('http://localhost:8080/api/auth/oauth/authorize');
    expect(upstreamUrl).toContain('provider=google');
    expect(upstreamUrl).toContain('redirectUri=');
  });

  it('returns 400 UNSUPPORTED_PROVIDER on unknown provider', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const req = new Request(
      'http://localhost/api/auth/oauth/authorize?provider=apple&redirectUri=http%3A%2F%2Fx',
    );
    const res = await authorizeGET(req);
    expect(res.status).toBe(400);
    expect((await res.json()).code).toBe('UNSUPPORTED_PROVIDER');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('returns 422 VALIDATION_ERROR when redirectUri missing', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const req = new Request('http://localhost/api/auth/oauth/authorize?provider=google');
    const res = await authorizeGET(req);
    expect(res.status).toBe(422);
    expect((await res.json()).code).toBe('VALIDATION_ERROR');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('forwards upstream error status and body', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ code: 'INTERNAL_ERROR', message: 'redis down' }), {
        status: 500,
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    vi.stubGlobal('fetch', fetchMock);

    const req = new Request(
      'http://localhost/api/auth/oauth/authorize?provider=google&redirectUri=http%3A%2F%2Fx',
    );
    const res = await authorizeGET(req);
    expect(res.status).toBe(500);
    expect((await res.json()).code).toBe('INTERNAL_ERROR');
  });

  it('returns 502 PROVIDER_ERROR when upstream fetch throws', async () => {
    const fetchMock = vi.fn().mockRejectedValue(new Error('network'));
    vi.stubGlobal('fetch', fetchMock);

    const req = new Request(
      'http://localhost/api/auth/oauth/authorize?provider=google&redirectUri=http%3A%2F%2Fx',
    );
    const res = await authorizeGET(req);
    expect(res.status).toBe(502);
    expect((await res.json()).code).toBe('PROVIDER_ERROR');
  });
});

describe('POST /api/auth/oauth/callback', () => {
  it('on success, sets accessToken/refreshToken cookies and returns ok+isNewAccount', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          accessToken: 'access.jwt',
          refreshToken: 'refresh.jwt',
          expiresIn: 1800,
          refreshExpiresIn: 604800,
          isNewAccount: true,
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    );
    vi.stubGlobal('fetch', fetchMock);

    const req = new Request('http://localhost/api/auth/oauth/callback', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        provider: 'google',
        code: 'authcode',
        state: 'state-xyz',
        redirectUri: 'http://localhost:3000/oauth/callback',
      }),
    });
    const res = await callbackPOST(req);
    expect(res.status).toBe(200);
    expect(await res.json()).toEqual({ ok: true, isNewAccount: true });

    const access = cookieJar.get('accessToken');
    const refresh = cookieJar.get('refreshToken');
    expect(access?.value).toBe('access.jwt');
    expect(access?.opts).toMatchObject({ httpOnly: true, secure: true, sameSite: 'strict', path: '/' });
    expect(access?.opts.maxAge).toBe(1800);
    expect(refresh?.value).toBe('refresh.jwt');
    expect(refresh?.opts.maxAge).toBe(604800);

    // Verify upstream call payload
    const [upstreamUrl, init] = fetchMock.mock.calls[0];
    expect(upstreamUrl).toBe('http://localhost:8080/api/auth/oauth/callback');
    expect((init as RequestInit).method).toBe('POST');
    const sentBody = JSON.parse((init as RequestInit).body as string);
    expect(sentBody).toEqual({
      provider: 'google',
      code: 'authcode',
      state: 'state-xyz',
      redirectUri: 'http://localhost:3000/oauth/callback',
    });
  });

  it('returns 422 VALIDATION_ERROR for malformed body', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);

    const req = new Request('http://localhost/api/auth/oauth/callback', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ provider: 'google' }),
    });
    const res = await callbackPOST(req);
    expect(res.status).toBe(422);
    expect((await res.json()).code).toBe('VALIDATION_ERROR');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('forwards upstream INVALID_STATE 401 without setting cookies', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ code: 'INVALID_STATE', message: 'expired' }), {
        status: 401,
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    vi.stubGlobal('fetch', fetchMock);

    const req = new Request('http://localhost/api/auth/oauth/callback', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        provider: 'google',
        code: 'c',
        state: 's',
        redirectUri: 'http://localhost:3000/oauth/callback',
      }),
    });
    const res = await callbackPOST(req);
    expect(res.status).toBe(401);
    expect((await res.json()).code).toBe('INVALID_STATE');
    expect(cookieJar.has('accessToken')).toBe(false);
    expect(cookieJar.has('refreshToken')).toBe(false);
  });

  it('forwards 422 EMAIL_REQUIRED', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ code: 'EMAIL_REQUIRED', message: 'no email' }), {
        status: 422,
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    vi.stubGlobal('fetch', fetchMock);

    const req = new Request('http://localhost/api/auth/oauth/callback', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        provider: 'kakao',
        code: 'c',
        state: 's',
        redirectUri: 'http://localhost:3000/oauth/callback',
      }),
    });
    const res = await callbackPOST(req);
    expect(res.status).toBe(422);
    expect((await res.json()).code).toBe('EMAIL_REQUIRED');
    expect(cookieJar.has('accessToken')).toBe(false);
  });

  it('forwards 403 ACCOUNT_LOCKED', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ code: 'ACCOUNT_LOCKED', message: 'locked' }), {
        status: 403,
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    vi.stubGlobal('fetch', fetchMock);

    const req = new Request('http://localhost/api/auth/oauth/callback', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        provider: 'google',
        code: 'c',
        state: 's',
        redirectUri: 'http://localhost:3000/oauth/callback',
      }),
    });
    const res = await callbackPOST(req);
    expect(res.status).toBe(403);
    expect((await res.json()).code).toBe('ACCOUNT_LOCKED');
    expect(cookieJar.has('accessToken')).toBe(false);
  });

  it('returns 502 PROVIDER_ERROR when upstream fetch throws', async () => {
    const fetchMock = vi.fn().mockRejectedValue(new Error('network'));
    vi.stubGlobal('fetch', fetchMock);

    const req = new Request('http://localhost/api/auth/oauth/callback', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        provider: 'google',
        code: 'c',
        state: 's',
        redirectUri: 'http://localhost:3000/oauth/callback',
      }),
    });
    const res = await callbackPOST(req);
    expect(res.status).toBe(502);
    expect((await res.json()).code).toBe('PROVIDER_ERROR');
  });
});
