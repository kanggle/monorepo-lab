// @vitest-environment node
/**
 * TASK-FAN-FE-027 — `getFanSession()` and `isAuthenticated()` are decode-only.
 *
 * 🔴 Both used to start with the request-less `auth()`, which runs the `jwt`
 * callback (= the silent refresh, rotating the refresh token at IAM) and drops
 * the rotated cookie. `isAuthenticated()` runs on EVERY page via the header,
 * so after the access token expired every page view spent the refresh token.
 * These cells pin: no `auth()`, no IAM call, same judgement as the `session`
 * callback, and the stale-bearer signal the header hands to `SessionKeeper`.
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

const state = vi.hoisted(() => ({
  token: null as Record<string, unknown> | null,
  throws: false,
  authCalls: 0,
}));

vi.mock('server-only', () => ({}));
vi.mock('next/headers', () => ({
  cookies: async () => ({ getAll: () => [{ name: 'authjs.session-token', value: 'enc' }] }),
}));
vi.mock('@/shared/auth/session-token', () => ({
  decodeSessionCookieHeader: async () => {
    if (state.throws) throw new Error('MissingSecret');
    return state.token;
  },
}));
vi.mock('@/shared/auth/auth', () => ({
  auth: async () => {
    state.authCalls += 1;
    return null;
  },
}));

import { getFanSession, isAuthenticated } from '@/shared/auth/session';

const nowSec = () => Math.floor(Date.now() / 1000);
const fetchMock = vi.fn();

beforeEach(() => {
  state.token = null;
  state.throws = false;
  state.authCalls = 0;
  fetchMock.mockReset();
  vi.stubGlobal('fetch', fetchMock);
});
afterEach(() => vi.unstubAllGlobals());

const base = {
  name: '팬',
  email: 'fan@example.com',
  accountId: 'acc-1',
  tenantId: 'fan-platform',
  roles: ['FAN'],
  accessToken: 'at-0',
  refreshToken: 'rt-0',
};

describe('decode-only server session reads', () => {
  it('🔴 expired-but-refreshable: signed in, old bearer, stale flagged — and NO refresh, NO auth()', async () => {
    state.token = { ...base, expiresAt: nowSec() - 600 };
    expect(await isAuthenticated()).toBe(true);
    const s = await getFanSession();
    expect(s.accountId).toBe('acc-1');
    expect(s.email).toBe('fan@example.com');
    expect(s.displayName).toBe('팬');
    expect(s.accessToken).toBe('at-0');
    expect(s.accessTokenStale).toBe(true);
    expect(fetchMock).not.toHaveBeenCalled();
    expect(state.authCalls).toBe(0);
  });

  it('fresh session: not stale', async () => {
    state.token = { ...base, expiresAt: nowSec() + 1800 };
    const s = await getFanSession();
    expect(s.accessTokenStale).toBe(false);
    expect(s.roles).toEqual(['FAN']);
  });

  it('failed refresh (error) → anonymous everywhere, no bearer', async () => {
    state.token = { ...base, expiresAt: nowSec() - 600, error: 'RefreshAccessTokenError' };
    expect(await isAuthenticated()).toBe(false);
    const s = await getFanSession();
    expect(s.accountId).toBeNull();
    expect(s.accessToken).toBeNull();
  });

  it('no cookie / decode throws → anonymous («cannot judge» is not «signed in»)', async () => {
    expect(await isAuthenticated()).toBe(false);
    expect((await getFanSession()).accountId).toBeNull();
    state.throws = true;
    expect(await isAuthenticated()).toBe(false);
    expect((await getFanSession()).accessToken).toBeNull();
  });
});
