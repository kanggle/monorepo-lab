import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * TASK-PC-FE-338 (ADR-MONO-083 D7 · OD-2 — console contract § 2.7 «Assumed-token TTL becomes
 * 300 s»). From P3 the assumed token lives 300 s while the base IAM token lives 1800 s, so for
 * 25 minutes of every 30 a selected tenant has a base token but no assumed token. Three things
 * must hold in that state:
 *
 *  1. the domain-facing resolver never hands out the BASE token for a selected tenant (it carries
 *     the login tenant, not the selected one) — it answers `null`, which every caller maps to 401
 *     and the browser client turns into refresh (= re-assume) + retry;
 *  2. the session is not «complete», so the refresh `GET` hop actually refreshes (it returned
 *     early on access + operator alone — the gate's hop would have looped);
 *  3. the cookie lapses BEFORE the token, so the re-assume happens before a domain server could
 *     see an expired token.
 */

const cookieJar = new Map<string, string>();
vi.mock('next/headers', () => ({
  cookies: async () => ({
    get: (n: string) => (cookieJar.has(n) ? { value: cookieJar.get(n)! } : undefined),
  }),
}));

import {
  ACCESS_COOKIE,
  OPERATOR_COOKIE,
  TENANT_COOKIE,
  ASSUMED_TOKEN_COOKIE,
  ASSUMED_TOKEN_EXPIRY_MARGIN_S,
  assumedTokenCookieMaxAge,
  getDomainFacingToken,
} from '@/shared/lib/session';
import { hasCompleteSession } from '@/shared/lib/session-refresh';
import { needsReassume } from '@/shared/lib/session';

const jar = {
  get: (n: string) => (cookieJar.has(n) ? { value: cookieJar.get(n)! } : undefined),
} as unknown as Parameters<typeof hasCompleteSession>[0];

beforeEach(() => {
  cookieJar.clear();
  cookieJar.set(ACCESS_COOKIE, 'base.token');
  cookieJar.set(OPERATOR_COOKIE, 'op.token');
});

describe('getDomainFacingToken — fail-closed for a selected tenant', () => {
  it('🔴 tenant selected, assumed cookie lapsed → null, never the base token', async () => {
    cookieJar.set(TENANT_COOKIE, 'demo-corp');
    expect(await getDomainFacingToken()).toBeNull();
  });

  it('tenant selected and assumed present → the assumed token', async () => {
    cookieJar.set(TENANT_COOKIE, 'demo-corp');
    cookieJar.set(ASSUMED_TOKEN_COOKIE, 'assumed.token');
    expect(await getDomainFacingToken()).toBe('assumed.token');
  });

  it('control: no tenant selected → the base token, exactly as before (net-zero)', async () => {
    expect(await getDomainFacingToken()).toBe('base.token');
  });
});

describe('hasCompleteSession — access + operator only (restored by the c-1 fix)', () => {
  it('🔴 access + operator + tenant, no assumed → still COMPLETE: the lapse is the re-assume hop\'s job, not a refresh (the first c-1 cut made the rotation-race retry end in session_expired)', () => {
    cookieJar.set(TENANT_COOKIE, 'demo-corp');
    expect(hasCompleteSession(jar)).toBe(true);
  });

  it('access + operator + tenant + assumed → complete', () => {
    cookieJar.set(TENANT_COOKIE, 'demo-corp');
    cookieJar.set(ASSUMED_TOKEN_COOKIE, 'assumed.token');
    expect(hasCompleteSession(jar)).toBe(true);
  });

  it('control: access + operator, no tenant → complete, exactly as before', () => {
    expect(hasCompleteSession(jar)).toBe(true);
  });

  it('control: no operator cookie → not complete, exactly as before', () => {
    cookieJar.delete(OPERATOR_COOKIE);
    expect(hasCompleteSession(jar)).toBe(false);
  });
});

describe('needsReassume — tenant selected, assumed lapsed, base alive', () => {
  it('🔴 tenant + no assumed + base → true', async () => {
    cookieJar.set(TENANT_COOKIE, 'demo-corp');
    expect(await needsReassume()).toBe(true);
  });

  it('assumed present → false · no tenant → false · no base token → false (the refresh hop handles that)', async () => {
    expect(await needsReassume()).toBe(false);
    cookieJar.set(TENANT_COOKIE, 'demo-corp');
    cookieJar.set(ASSUMED_TOKEN_COOKIE, 'assumed.token');
    expect(await needsReassume()).toBe(false);
    cookieJar.delete(ASSUMED_TOKEN_COOKIE);
    cookieJar.delete(ACCESS_COOKIE);
    expect(await needsReassume()).toBe(false);
  });
});

describe('assumedTokenCookieMaxAge — the cookie lapses before the token', () => {
  it('P3 TTL 300 s → cookie 240 s', () => {
    expect(ASSUMED_TOKEN_EXPIRY_MARGIN_S).toBe(60);
    expect(assumedTokenCookieMaxAge(300)).toBe(240);
  });

  it('today TTL 1800 s → cookie 1740 s (still before the token)', () => {
    expect(assumedTokenCookieMaxAge(1800)).toBe(1740);
  });

  it('a token too short for the margin keeps half its life, never 0 (0 would delete the cookie)', () => {
    expect(assumedTokenCookieMaxAge(100)).toBe(50);
    expect(assumedTokenCookieMaxAge(1)).toBe(1);
  });

  it('for every TTL the cookie is strictly shorter than the token, or 1 s at the floor', () => {
    for (const ttl of [2, 30, 119, 120, 121, 300, 900, 1800, 3600]) {
      const c = assumedTokenCookieMaxAge(ttl);
      expect(c).toBeGreaterThan(0);
      expect(c).toBeLessThan(ttl);
    }
  });
});
