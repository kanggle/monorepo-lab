/**
 * `isSampleVisitor()` — the ONE predicate (ADR-MONO-074 A1 / TASK-PC-FE-282 AC-1).
 *
 * 🔴🔴 The four cells are the point. Only «none ⇒ sample» would pass a predicate
 *    that also calls a half session a sample visitor — and that regression is
 *    silent (the pre-operator visitor sees synthetic data instead of onboarding;
 *    an operator whose access cookie expired sees synthetic data as if real).
 *
 * Real `session.ts`, real cookie reads — only `next/headers` is replaced.
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';

const cookieJar = new Map<string, string>();
vi.mock('next/headers', () => ({
  cookies: async () => ({
    get: (n: string) => (cookieJar.has(n) ? { value: cookieJar.get(n)! } : undefined),
  }),
}));

import {
  isSampleVisitor,
  isAuthenticated,
  getActiveTenant,
  ACCESS_COOKIE,
  OPERATOR_COOKIE,
  TENANT_COOKIE,
} from '@/shared/lib/session';
import { SAMPLE_TENANT_ID } from '@/shared/sample/codes';

beforeEach(() => {
  cookieJar.clear();
});

describe('isSampleVisitor — 4 cells (AC-1)', () => {
  it('access ✗ · operator ✗ → sample visitor', async () => {
    expect(await isSampleVisitor()).toBe(true);
  });

  it('🔴 access ✓ · operator ✗ (pre-operator half session) → NOT a sample visitor', async () => {
    cookieJar.set(ACCESS_COOKIE, 'a');
    expect(await isSampleVisitor()).toBe(false);
  });

  it('🔴 access ✗ · operator ✓ (half session) → NOT a sample visitor', async () => {
    cookieJar.set(OPERATOR_COOKIE, 'o');
    expect(await isSampleVisitor()).toBe(false);
  });

  it('access ✓ · operator ✓ (authenticated) → NOT a sample visitor', async () => {
    cookieJar.set(ACCESS_COOKIE, 'a');
    cookieJar.set(OPERATOR_COOKIE, 'o');
    expect(await isSampleVisitor()).toBe(false);
    expect(await isAuthenticated()).toBe(true);
  });

  it('🔵 sample and authenticated are never both true (exhaustive over the 4 cells)', async () => {
    for (const [a, o] of [
      [false, false],
      [true, false],
      [false, true],
      [true, true],
    ] as const) {
      cookieJar.clear();
      if (a) cookieJar.set(ACCESS_COOKIE, 'a');
      if (o) cookieJar.set(OPERATOR_COOKIE, 'o');
      expect((await isSampleVisitor()) && (await isAuthenticated())).toBe(false);
    }
  });
});

describe('getActiveTenant — the sample tenant (A7)', () => {
  it('sample visitor → the single sample tenant, ignoring a stale tenant cookie', async () => {
    cookieJar.set(TENANT_COOKIE, 'acme');
    expect(await getActiveTenant()).toBe(SAMPLE_TENANT_ID);
  });

  it('🔵 control — authenticated operator → the tenant cookie, unchanged', async () => {
    cookieJar.set(ACCESS_COOKIE, 'a');
    cookieJar.set(OPERATOR_COOKIE, 'o');
    cookieJar.set(TENANT_COOKIE, 'acme');
    expect(await getActiveTenant()).toBe('acme');
  });

  it('🔵 control — authenticated operator without a tenant → null, unchanged', async () => {
    cookieJar.set(ACCESS_COOKIE, 'a');
    cookieJar.set(OPERATOR_COOKIE, 'o');
    expect(await getActiveTenant()).toBeNull();
  });

  it('🔵 control — half session → the cookie read, never the sample tenant', async () => {
    cookieJar.set(ACCESS_COOKIE, 'a');
    expect(await getActiveTenant()).toBeNull();
  });
});
