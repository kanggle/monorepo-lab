/**
 * Unit tests for auth-callbacks.ts (Phase 4.5 F3 — fan-platform-web OIDC
 * silent refresh).
 *
 * The module is next-auth-free, so these tests run in the plain Node/jsdom
 * vitest environment without triggering the `NextAuth()` factory.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
  refreshAccessToken,
  refreshTokenGrant,
  hasFreshAccessToken,
  publicSessionFromToken,
  REFRESH_RACE_LOST_CLAIM,
  jwtCallback,
  sessionCallback,
  selectAccessToken,
  REFRESH_MARGIN_SECONDS,
} from '@/shared/auth/auth-callbacks';
import { hasAuthenticatedUser } from '@/shared/auth/session-shape';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

const NOW_S = 1_700_000_000; // arbitrary fixed unix timestamp (seconds)
const NOW_MS = NOW_S * 1000;

function makeTokenResponse(overrides?: Partial<{
  access_token: string;
  refresh_token: string;
  id_token: string;
  expires_in: number;
}>) {
  return {
    access_token: 'new-access',
    refresh_token: 'new-refresh',
    id_token: 'new-id',
    expires_in: 1800,
    ...overrides,
  };
}

// ---------------------------------------------------------------------------
// refreshAccessToken
// ---------------------------------------------------------------------------

describe('refreshAccessToken', () => {
  const realFetch = global.fetch;

  beforeEach(() => {
    vi.stubEnv('OIDC_ISSUER_URL', 'http://iam.test');
    vi.stubEnv('OIDC_CLIENT_ID', 'fan-client');
    vi.stubEnv('OIDC_CLIENT_SECRET', 'fan-secret');
    vi.useFakeTimers();
    vi.setSystemTime(NOW_MS);
  });

  afterEach(() => {
    global.fetch = realFetch;
    vi.unstubAllEnvs();
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('returns rotated tokens on a successful 200 response', async () => {
    global.fetch = vi.fn(async () =>
      new Response(JSON.stringify(makeTokenResponse()), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    ) as unknown as typeof fetch;

    const result = await refreshAccessToken('old-refresh');

    expect(result).not.toBeNull();
    expect(result!.accessToken).toBe('new-access');
    expect(result!.refreshToken).toBe('new-refresh');
    expect(result!.idToken).toBe('new-id');
    // expiresAt = NOW_S + expires_in
    expect(result!.expiresAt).toBe(NOW_S + 1800);
  });

  it('falls back to the sent refresh token when IAM omits refresh_token', async () => {
    const body = makeTokenResponse({ refresh_token: undefined });
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    delete (body as any).refresh_token;
    global.fetch = vi.fn(async () =>
      new Response(JSON.stringify(body), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    ) as unknown as typeof fetch;

    const result = await refreshAccessToken('old-refresh');

    expect(result).not.toBeNull();
    // rotation fallback: reuse sent token
    expect(result!.refreshToken).toBe('old-refresh');
  });

  it('returns null for a non-2xx response (e.g. 401)', async () => {
    global.fetch = vi.fn(async () =>
      new Response(JSON.stringify({ error: 'invalid_grant' }), {
        status: 401,
        headers: { 'Content-Type': 'application/json' },
      }),
    ) as unknown as typeof fetch;

    const result = await refreshAccessToken('bad-refresh');
    expect(result).toBeNull();
  });

  it('returns null when the response body lacks access_token', async () => {
    global.fetch = vi.fn(async () =>
      new Response(JSON.stringify({ token_type: 'Bearer' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    ) as unknown as typeof fetch;

    const result = await refreshAccessToken('ok-refresh');
    expect(result).toBeNull();
  });

  it('returns null on a network error (fetch throws)', async () => {
    global.fetch = vi.fn(async () => {
      throw new Error('ECONNREFUSED');
    }) as unknown as typeof fetch;

    const result = await refreshAccessToken('any-refresh');
    expect(result).toBeNull();
  });

  it('posts client_secret_basic Authorization header', async () => {
    let capturedHeaders: Record<string, string> = {};
    global.fetch = vi.fn(async (_url: string | URL | Request, init?: RequestInit) => {
      capturedHeaders = (init?.headers as Record<string, string>) ?? {};
      return new Response(JSON.stringify(makeTokenResponse()), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    }) as unknown as typeof fetch;

    await refreshAccessToken('r');

    // env module reads process.env at import time; the default clientId is
    // 'fan-platform-user-flow-client' and the default secret is ''.
    // We assert the format (Basic scheme + base64 colon-pair) rather than a
    // specific credential value so the test is env-agnostic.
    expect(capturedHeaders['Authorization']).toMatch(/^Basic [A-Za-z0-9+/]+=*$/);
  });
});

// ---------------------------------------------------------------------------
// jwtCallback — sign-in (account present)
// ---------------------------------------------------------------------------

describe('jwtCallback — initial sign-in', () => {
  it('stores tokens from account and clears any prior error', async () => {
    const token = await jwtCallback({
      token: { error: 'RefreshAccessTokenError' },
      account: {
        access_token: 'at',
        refresh_token: 'rt',
        expires_at: 9999,
        id_token: 'idt',
      },
    });

    expect(token.accessToken).toBe('at');
    expect(token.refreshToken).toBe('rt');
    expect(token.expiresAt).toBe(9999);
    expect(token.idToken).toBe('idt');
    expect(token.error).toBeUndefined();
  });

  it('merges profile claims (tenant_id, account_id, roles)', async () => {
    const token = await jwtCallback({
      token: {},
      account: { access_token: 'at', refresh_token: 'rt' },
      profile: { sub: 'u1', tenant_id: 'fan-platform', account_id: 'acc1', roles: ['CUSTOMER'] },
    });

    expect(token.tenantId).toBe('fan-platform');
    expect(token.accountId).toBe('acc1');
    expect(token.roles).toEqual(['CUSTOMER']);
  });

  it('merges user claims for profile-less providers', async () => {
    const token = await jwtCallback({
      token: {},
      account: { access_token: 'at' },
      user: { accountId: 'acc2', tenantId: 'fp', roles: ['CUSTOMER'] },
    });

    expect(token.accountId).toBe('acc2');
    expect(token.tenantId).toBe('fp');
  });
});

// ---------------------------------------------------------------------------
// jwtCallback — subsequent calls (no account) — proactive refresh decision
// ---------------------------------------------------------------------------

describe('jwtCallback — proactive refresh', () => {
  const realFetch = global.fetch;

  beforeEach(() => {
    vi.stubEnv('OIDC_ISSUER_URL', 'http://iam.test');
    vi.stubEnv('OIDC_CLIENT_ID', 'fan-client');
    vi.stubEnv('OIDC_CLIENT_SECRET', 'fan-secret');
    vi.useFakeTimers();
    vi.setSystemTime(NOW_MS);
  });

  afterEach(() => {
    global.fetch = realFetch;
    vi.unstubAllEnvs();
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('does NOT refresh when the token is still valid (> margin left)', async () => {
    const fetchSpy = vi.fn();
    global.fetch = fetchSpy as unknown as typeof fetch;

    // expiresAt is well beyond the margin
    const expiresAt = NOW_S + REFRESH_MARGIN_SECONDS + 120;
    const token = await jwtCallback({
      token: {
        accessToken: 'old-at',
        refreshToken: 'old-rt',
        expiresAt,
      },
    });

    expect(fetchSpy).not.toHaveBeenCalled();
    expect(token.accessToken).toBe('old-at');
  });

  it('refreshes when the token is within the 60s expiry margin', async () => {
    global.fetch = vi.fn(async () =>
      new Response(JSON.stringify(makeTokenResponse()), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    ) as unknown as typeof fetch;

    // expiresAt is exactly at margin boundary → triggers refresh
    const expiresAt = NOW_S + REFRESH_MARGIN_SECONDS - 1;
    const token = await jwtCallback({
      token: {
        accessToken: 'old-at',
        refreshToken: 'old-rt',
        expiresAt,
      },
    });

    expect(token.accessToken).toBe('new-access');
    expect(token.refreshToken).toBe('new-refresh');
    expect(token.error).toBeUndefined();
  });

  it('uses rotated refresh token after successful refresh', async () => {
    global.fetch = vi.fn(async () =>
      new Response(
        JSON.stringify(makeTokenResponse({ refresh_token: 'rotated-rt' })),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    ) as unknown as typeof fetch;

    const expiresAt = NOW_S - 10; // already expired
    const token = await jwtCallback({
      token: { accessToken: 'old-at', refreshToken: 'old-rt', expiresAt },
    });

    expect(token.refreshToken).toBe('rotated-rt');
  });

  it('sets token.error on refresh failure and does not loop', async () => {
    global.fetch = vi.fn(async () =>
      new Response(JSON.stringify({ error: 'invalid_grant' }), {
        status: 401,
        headers: { 'Content-Type': 'application/json' },
      }),
    ) as unknown as typeof fetch;

    const expiresAt = NOW_S - 10;
    const token = await jwtCallback({
      token: { accessToken: 'old-at', refreshToken: 'old-rt', expiresAt },
    });

    expect(token.error).toBe('RefreshAccessTokenError');
  });

  it('does NOT attempt refresh when token.error is already set (no loop)', async () => {
    const fetchSpy = vi.fn();
    global.fetch = fetchSpy as unknown as typeof fetch;

    const expiresAt = NOW_S - 10; // expired
    const token = await jwtCallback({
      token: {
        accessToken: 'old-at',
        refreshToken: 'old-rt',
        expiresAt,
        error: 'RefreshAccessTokenError',
      },
    });

    expect(fetchSpy).not.toHaveBeenCalled();
    expect(token.error).toBe('RefreshAccessTokenError');
  });

  it('does NOT attempt refresh when no refreshToken is held', async () => {
    const fetchSpy = vi.fn();
    global.fetch = fetchSpy as unknown as typeof fetch;

    const expiresAt = NOW_S - 10;
    await jwtCallback({
      token: { accessToken: 'old-at', expiresAt },
    });

    expect(fetchSpy).not.toHaveBeenCalled();
  });
});

// ---------------------------------------------------------------------------
// sessionCallback — degrade to anonymous on RefreshAccessTokenError
// ---------------------------------------------------------------------------

describe('sessionCallback', () => {
  it('exposes only identity claims (no tokens) on a healthy session', () => {
    const session = sessionCallback({
      session: { expires: '2099-01-01' },
      token: {
        accountId: 'acc1',
        tenantId: 'fan-platform',
        roles: ['CUSTOMER'],
        accessToken: 'secret-at',
        refreshToken: 'secret-rt',
      },
    });

    expect(session.accountId).toBe('acc1');
    expect(session.tenantId).toBe('fan-platform');
    expect(session.roles).toEqual(['CUSTOMER']);
    // Tokens MUST NOT appear on the session (F2)
    expect(session.accessToken).toBeUndefined();
    expect(session.refreshToken).toBeUndefined();
  });

  it('degrades to anonymous when token.error = RefreshAccessTokenError', () => {
    const session = sessionCallback({
      session: { expires: '2099-01-01' },
      token: {
        accountId: 'acc1',
        tenantId: 'fan-platform',
        roles: ['CUSTOMER'],
        error: 'RefreshAccessTokenError',
      },
    });

    expect(session.accountId).toBeNull();
    expect(session.tenantId).toBeNull();
    expect(session.roles).toEqual([]);
    expect(session.user).toBeUndefined();
  });

  it('defaults missing claims to null / empty array', () => {
    const session = sessionCallback({
      session: { expires: '2099-01-01' },
      token: {},
    });

    expect(session.accountId).toBeNull();
    expect(session.tenantId).toBeNull();
    expect(session.roles).toEqual([]);
  });
});

// ---------------------------------------------------------------------------
// selectAccessToken — server-side bearer read from the raw JWT (TASK-FAN-FE-008)
// ---------------------------------------------------------------------------

describe('selectAccessToken', () => {
  it('returns the accessToken from a healthy JWT (the token sessionCallback strips)', () => {
    // The SAME token shape the session callback deliberately removes (F2) — the
    // server-side gateway fetch must still get it from the raw JWT.
    expect(selectAccessToken({ accessToken: 'at-123', accountId: 'acc1' })).toBe('at-123');
  });

  it('returns null when a silent refresh failed (RefreshAccessTokenError)', () => {
    // A known-stale token must NOT be sent — the caller behaves as unauthenticated.
    expect(
      selectAccessToken({ accessToken: 'stale-at', error: 'RefreshAccessTokenError' }),
    ).toBeNull();
  });

  it('returns null for a missing / empty / non-string accessToken', () => {
    expect(selectAccessToken({})).toBeNull();
    expect(selectAccessToken({ accessToken: '' })).toBeNull();
    expect(selectAccessToken({ accessToken: 123 as unknown as string })).toBeNull();
  });

  it('returns null for a null / undefined JWT (no session cookie)', () => {
    expect(selectAccessToken(null)).toBeNull();
    expect(selectAccessToken(undefined)).toBeNull();
  });
});

// ---------------------------------------------------------------------------
// TASK-FAN-FE-027 — rotation-race classification (copied from web-store
// TASK-FE-106). IAM answers the LOSER of a same-token concurrent refresh with a
// bare `400 invalid_grant` inside its 30s grace window (iam TASK-BE-606/608).
// ---------------------------------------------------------------------------

describe('TASK-FAN-FE-027 — rotation-race classification', () => {
  const fetchMock = vi.fn();
  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock);
    fetchMock.mockReset();
  });
  afterEach(() => {
    vi.unstubAllGlobals();
  });
  const nowSec = () => Math.floor(Date.now() / 1000);
  const expired = () => ({
    accessToken: 'old',
    refreshToken: 'rt-0',
    expiresAt: nowSec() - 10,
    accountId: 'acc-1',
    roles: ['FAN'],
  });

  it('400 invalid_grant → rotation_suspect', async () => {
    fetchMock.mockResolvedValue({ ok: false, status: 400, json: async () => ({ error: 'invalid_grant' }) });
    expect(await refreshTokenGrant('rt-0')).toEqual({ kind: 'rotation_suspect' });
  });

  it.each([
    ['400 invalid_client', 400, { error: 'invalid_client' }],
    ['401', 401, { error: 'invalid_grant' }],
    ['503', 503, {}],
  ])('%s → failed (경합 모양이 아님)', async (_label, status, body) => {
    fetchMock.mockResolvedValue({ ok: false, status, json: async () => body });
    expect(await refreshTokenGrant('rt-0')).toEqual({ kind: 'failed' });
  });

  it('경합 패자: error(종결) + refreshRaceLost 를 둘 다 단다', async () => {
    fetchMock.mockResolvedValue({ ok: false, status: 400, json: async () => ({ error: 'invalid_grant' }) });
    const token = await jwtCallback({ token: expired(), account: null });
    // Terminal if the cookie ever escapes: never a silent replay of rt-0.
    expect(token.error).toBe('RefreshAccessTokenError');
    expect(token[REFRESH_RACE_LOST_CLAIM]).toBe(true);
  });

  it('🔵 대조군 — 경합 모양이 아닌 실패에는 refreshRaceLost 를 달지 않는다', async () => {
    fetchMock.mockResolvedValue({ ok: false, status: 503, json: async () => ({}) });
    const token = await jwtCallback({ token: expired(), account: null });
    expect(token.error).toBe('RefreshAccessTokenError');
    expect(token[REFRESH_RACE_LOST_CLAIM]).toBeUndefined();
  });

  it('refreshRaceLost 는 그 호출에서만 — 다음 jwt 호출이 지우고, error 가 있으니 재전송도 없다', async () => {
    const token = await jwtCallback({
      token: { ...expired(), error: 'RefreshAccessTokenError', [REFRESH_RACE_LOST_CLAIM]: true },
      account: null,
    });
    expect(token[REFRESH_RACE_LOST_CLAIM]).toBeUndefined();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('session 콜백은 패자 표지를 익명 세션에 boolean 으로만 싣는다', () => {
    const s = sessionCallback({
      session: {},
      token: { ...expired(), error: 'RefreshAccessTokenError', [REFRESH_RACE_LOST_CLAIM]: true },
    });
    expect(s.accountId).toBeNull();
    expect(s[REFRESH_RACE_LOST_CLAIM]).toBe(true);
    expect(JSON.stringify(s)).not.toContain('rt-0');
    const plain = sessionCallback({ session: {}, token: { ...expired(), error: 'RefreshAccessTokenError' } });
    expect(plain).not.toHaveProperty(REFRESH_RACE_LOST_CLAIM);
  });

  it('hasFreshAccessToken — 만료 60초 전부터는 신선하지 않다', () => {
    const now = Date.now();
    const sec = Math.floor(now / 1000);
    expect(hasFreshAccessToken({ expiresAt: sec + 3600 }, now)).toBe(true);
    expect(hasFreshAccessToken({ expiresAt: sec + 30 }, now)).toBe(false);
    expect(hasFreshAccessToken({}, now)).toBe(false);
  });
});

// ---------------------------------------------------------------------------
// TASK-FAN-FE-027 — publicSessionFromToken: the decode-only readers' judge.
// It must answer what `/api/auth/session` would answer, WITHOUT refreshing.
// ---------------------------------------------------------------------------

describe('publicSessionFromToken (decode-only judge)', () => {
  const fetchMock = vi.fn();
  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock);
    fetchMock.mockReset();
  });
  afterEach(() => {
    vi.unstubAllGlobals();
  });
  const nowSec = () => Math.floor(Date.now() / 1000);

  it('🔴 만료됐지만 refresh 가능한 세션 → 인증됨, IAM 호출 0', () => {
    const s = publicSessionFromToken({
      name: '팬',
      email: 'fan@example.com',
      accountId: 'acc-1',
      tenantId: 'fan-platform',
      roles: ['FAN'],
      accessToken: 'expired-at',
      refreshToken: 'rt-0',
      expiresAt: nowSec() - 600,
    });
    expect(hasAuthenticatedUser(s)).toBe(true);
    expect(s.accountId).toBe('acc-1');
    expect((s.user as { email?: string }).email).toBe('fan@example.com');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('refresh 실패 플래그(error) → 익명 (session 콜백과 같은 규칙)', () => {
    const s = publicSessionFromToken({ accountId: 'acc-1', error: 'RefreshAccessTokenError' });
    expect(hasAuthenticatedUser(s)).toBe(false);
  });

  it('토큰을 공개 세션에 싣지 않는다 (F2)', () => {
    const s = publicSessionFromToken({ accessToken: 'secret-at', refreshToken: 'secret-rt', idToken: 'secret-id' });
    expect(JSON.stringify(s)).not.toMatch(/secret-/);
  });
});
