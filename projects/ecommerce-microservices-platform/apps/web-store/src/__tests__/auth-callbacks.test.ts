import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
  refreshAccessToken,
  jwtCallback,
  sessionCallback,
} from '@/shared/auth/auth-callbacks';

/**
 * Unit tests for the NextAuth callback logic + silent-refresh helper added by
 * TASK-FE-075 (consumer-integration-guide § Phase 4.5 F2/F3). These exercise
 * the pure `auth-callbacks` module (no `next-auth` import) — `auth.ts` wires
 * these same functions into the NextAuthConfig.
 *
 *   - F2: the `session` callback must NEVER expose `accessToken` on the public
 *     session, for both the consumer and the degraded (operator / refresh-fail)
 *     branches.
 *   - F3: `refreshAccessToken` exchanges the refresh token at /oauth2/token and
 *     stores the rotated pair; the `jwt` callback refreshes near expiry and
 *     flags `RefreshAccessTokenError` on failure.
 */

/* eslint-disable @typescript-eslint/no-explicit-any */
const sessionCb = (args: any) => sessionCallback(args);
const jwtCb = (args: any) => jwtCallback(args);
/* eslint-enable @typescript-eslint/no-explicit-any */

describe('auth.ts session callback — F2 token confidentiality', () => {
  it('CONSUMER 세션에 accessToken 을 노출하지 않는다', async () => {
    const session = await sessionCb({
      session: { user: { email: 'c@test.com' }, expires: '2099-01-01T00:00:00Z' },
      token: {
        roles: ['CUSTOMER'],
        accountId: 'acc-1',
        tenantId: 'ecommerce',
        accessToken: 'secret-bearer',
        refreshToken: 'secret-refresh',
        idToken: 'secret-id',
      },
    });

    expect(session.accountId).toBe('acc-1');
    expect(session.tenantId).toBe('ecommerce');
    expect(session.roles).toEqual(['CUSTOMER']);
    // The bearer / refresh / id tokens must not leak onto the public session.
    expect(session).not.toHaveProperty('accessToken');
    expect(session).not.toHaveProperty('refreshToken');
    expect(session).not.toHaveProperty('idToken');
    expect(JSON.stringify(session)).not.toContain('secret-bearer');
  });

  it('operator(CUSTOMER 미보유)는 degraded(익명) 세션 + accessToken 없음', async () => {
    const session = await sessionCb({
      session: { user: { email: 'op@test.com' }, expires: '2099-01-01T00:00:00Z' },
      token: { roles: ['ECOMMERCE_OPERATOR'], accountId: 'op-1', accessToken: 'secret-bearer' },
    });

    expect(session.accountId).toBeNull();
    expect(session.roles).toEqual([]);
    expect(session.user).toBeUndefined();
    expect(session).not.toHaveProperty('accessToken');
  });

  it('refresh 실패(error) 세션은 degraded 처리된다 (F3 fallback)', async () => {
    const session = await sessionCb({
      session: { user: { email: 'c@test.com' }, expires: '2099-01-01T00:00:00Z' },
      token: {
        roles: ['CUSTOMER'],
        accountId: 'acc-1',
        accessToken: 'stale',
        error: 'RefreshAccessTokenError',
      },
    });
    expect(session.accountId).toBeNull();
    expect(session.user).toBeUndefined();
    expect(session).not.toHaveProperty('accessToken');
  });
});

describe('refreshAccessToken — F3 rotation', () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock);
    fetchMock.mockReset();
  });
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('200 → 회전된 access/refresh pair 를 반환한다', async () => {
    fetchMock.mockResolvedValue({
      ok: true,
      json: async () => ({
        access_token: 'new-access',
        refresh_token: 'new-refresh',
        id_token: 'new-id',
        expires_in: 1800,
      }),
    });

    const result = await refreshAccessToken('old-refresh');
    expect(result).not.toBeNull();
    expect(result!.accessToken).toBe('new-access');
    expect(result!.refreshToken).toBe('new-refresh'); // rotation
    expect(result!.idToken).toBe('new-id');
    expect(result!.expiresAt).toBeGreaterThan(Math.floor(Date.now() / 1000));

    // Posts grant_type=refresh_token to /oauth2/token with Basic auth.
    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toContain('/oauth2/token');
    expect(init.method).toBe('POST');
    expect(init.headers.Authorization).toMatch(/^Basic /);
    expect(String(init.body)).toContain('grant_type=refresh_token');
    expect(String(init.body)).toContain('refresh_token=old-refresh');
  });

  it('refresh_token 미반환 시 기존 refresh 를 유지한다', async () => {
    fetchMock.mockResolvedValue({
      ok: true,
      json: async () => ({ access_token: 'new-access', expires_in: 1800 }),
    });
    const result = await refreshAccessToken('old-refresh');
    expect(result!.refreshToken).toBe('old-refresh');
  });

  it('non-2xx → null', async () => {
    fetchMock.mockResolvedValue({ ok: false, status: 400, json: async () => ({}) });
    expect(await refreshAccessToken('bad')).toBeNull();
  });

  it('network 예외 → null', async () => {
    fetchMock.mockRejectedValue(new Error('ECONNREFUSED'));
    expect(await refreshAccessToken('x')).toBeNull();
  });
});

describe('auth.ts jwt callback — F3 silent refresh', () => {
  const fetchMock = vi.fn();
  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock);
    fetchMock.mockReset();
  });
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  const nowSec = () => Math.floor(Date.now() / 1000);

  it('만료 임박 토큰을 refresh 하고 회전 결과를 저장한다', async () => {
    fetchMock.mockResolvedValue({
      ok: true,
      json: async () => ({
        access_token: 'rotated-access',
        refresh_token: 'rotated-refresh',
        expires_in: 1800,
      }),
    });

    const token = await jwtCb({
      token: {
        accessToken: 'old-access',
        refreshToken: 'old-refresh',
        expiresAt: nowSec() - 10, // already expired
        roles: ['CUSTOMER'],
      },
      account: null,
    });

    expect(token.accessToken).toBe('rotated-access');
    expect(token.refreshToken).toBe('rotated-refresh');
    expect(token.error).toBeUndefined();
    expect(fetchMock).toHaveBeenCalledOnce();
  });

  it('유효한 토큰은 refresh 하지 않는다', async () => {
    const token = await jwtCb({
      token: {
        accessToken: 'live-access',
        refreshToken: 'r',
        expiresAt: nowSec() + 3600, // far from expiry
        roles: ['CUSTOMER'],
      },
      account: null,
    });
    expect(token.accessToken).toBe('live-access');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('refresh 실패 시 RefreshAccessTokenError 를 플래그한다', async () => {
    fetchMock.mockResolvedValue({ ok: false, status: 400, json: async () => ({}) });
    const token = await jwtCb({
      token: {
        accessToken: 'old',
        refreshToken: 'bad-refresh',
        expiresAt: nowSec() - 10,
        roles: ['CUSTOMER'],
      },
      account: null,
    });
    expect(token.error).toBe('RefreshAccessTokenError');
  });

  it('최초 sign-in(account 있음)은 토큰을 저장하고 error 를 지운다', async () => {
    const token = await jwtCb({
      token: { error: 'RefreshAccessTokenError' },
      account: {
        access_token: 'a',
        refresh_token: 'r',
        expires_at: nowSec() + 1800,
        id_token: 'i',
      },
      profile: { tenant_id: 'ecommerce', account_id: 'acc-1', roles: ['CUSTOMER'] },
    });
    expect(token.accessToken).toBe('a');
    expect(token.refreshToken).toBe('r');
    expect(token.idToken).toBe('i');
    expect(token.error).toBeUndefined();
    // No refresh attempt on initial sign-in.
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
