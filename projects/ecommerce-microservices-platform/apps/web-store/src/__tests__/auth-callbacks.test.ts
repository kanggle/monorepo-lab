import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
  refreshAccessToken,
  refreshTokenGrant,
  hasFreshAccessToken,
  REFRESH_RACE_LOST_CLAIM,
  jwtCallback,
  sessionCallback,
  signInCallback,
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

/**
 * TASK-BE-605 (α) — the storefront role guard at sign-in, pinned without a running IAM. The
 * full-stack proof is `e2e/account-type-guard.spec.ts` (nightly lane only); this is what a PR
 * run can see. The guard admits on `CUSTOMER` and on nothing else (ADR-MONO-035 §4b-1).
 */
describe('auth.ts signIn callback — CUSTOMER role guard', () => {
  const MISMATCH = '/login?error=account_type_mismatch';

  it('CUSTOMER 보유 → 입장(true)', () => {
    expect(signInCallback({ sub: 'acc-1', tenant_id: 'ecommerce', roles: ['CUSTOMER'] })).toBe(true);
  });

  it('다른 역할과 함께여도 CUSTOMER 가 있으면 입장', () => {
    expect(signInCallback({ sub: 'acc-1', roles: ['ECOMMERCE_OPERATOR', 'CUSTOMER'] })).toBe(true);
  });

  it('CUSTOMER 없는 역할(운영자 전용) → account_type_mismatch 로 되돌린다', () => {
    expect(signInCallback({ sub: 'op-1', tenant_id: 'ecommerce', roles: ['ECOMMERCE_OPERATOR'] }))
      .toBe(MISMATCH);
  });

  it('roles claim 부재 · 빈 배열 · profile 없음 → 거부 (교차 테넌트 토큰의 모양)', () => {
    expect(signInCallback({ sub: 'x', tenant_id: '*' })).toBe(MISMATCH);
    expect(signInCallback({ sub: 'x', roles: [] })).toBe(MISMATCH);
    expect(signInCallback(undefined)).toBe(MISMATCH);
  });

  it('대소문자가 다른 역할 이름은 CUSTOMER 가 아니다 (정확 일치)', () => {
    expect(signInCallback({ sub: 'x', roles: ['customer'] })).toBe(MISMATCH);
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

/**
 * TASK-FE-106 — IAM answers the LOSER of a same-token concurrent refresh with a
 * bare `400 invalid_grant` inside its 30s grace window (iam TASK-BE-606/608).
 */
describe('TASK-FE-106 — rotation-race classification', () => {
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
    roles: ['CUSTOMER'],
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
    const token = await jwtCb({ token: expired(), account: null });
    // Terminal if the cookie ever escapes: never a silent replay of rt-0.
    expect(token.error).toBe('RefreshAccessTokenError');
    expect(token[REFRESH_RACE_LOST_CLAIM]).toBe(true);
  });

  it('🔵 대조군 — 경합 모양이 아닌 실패에는 refreshRaceLost 를 달지 않는다', async () => {
    fetchMock.mockResolvedValue({ ok: false, status: 503, json: async () => ({}) });
    const token = await jwtCb({ token: expired(), account: null });
    expect(token.error).toBe('RefreshAccessTokenError');
    expect(token[REFRESH_RACE_LOST_CLAIM]).toBeUndefined();
  });

  it('refreshRaceLost 는 그 호출에서만 — 다음 jwt 호출이 지우고, error 가 있으니 재전송도 없다', async () => {
    const token = await jwtCb({
      token: { ...expired(), error: 'RefreshAccessTokenError', [REFRESH_RACE_LOST_CLAIM]: true },
      account: null,
    });
    expect(token[REFRESH_RACE_LOST_CLAIM]).toBeUndefined();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('session 콜백은 패자 표지를 익명 세션에 boolean 으로만 싣는다', () => {
    const s = sessionCb({
      session: {},
      token: { ...expired(), error: 'RefreshAccessTokenError', [REFRESH_RACE_LOST_CLAIM]: true },
    });
    expect(s.accountId).toBeNull();
    expect(s[REFRESH_RACE_LOST_CLAIM]).toBe(true);
    expect(JSON.stringify(s)).not.toContain('rt-0');
    const plain = sessionCb({ session: {}, token: { ...expired(), error: 'RefreshAccessTokenError' } });
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
