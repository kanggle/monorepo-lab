import { describe, it, expect } from 'vitest';
import { accountsAccessTier } from '@/shared/guide/permission-map';

/**
 * TASK-PC-FE-326 — `accountsAccessTier()` pure-function unit tests.
 *
 * A caller lacking `account.read` but holding account.lock/unlock/
 * force_logout (SUPPORT_LOCK — CS L2, or SECURITY_ANALYST) must open
 * `/accounts` in `'search-only'` mode, never the hidden+forbidden `'none'`
 * state a role holding none of the four permissions still gets.
 */
describe('accountsAccessTier', () => {
  it('account.read holder → full (SUPER_ADMIN, SUPPORT_READONLY unchanged)', () => {
    expect(accountsAccessTier(['SUPER_ADMIN'])).toBe('full');
    expect(accountsAccessTier(['SUPPORT_READONLY'])).toBe('full');
  });

  it('SUPPORT_LOCK (no account.read, holds lock/unlock/force_logout) → search-only', () => {
    expect(accountsAccessTier(['SUPPORT_LOCK'])).toBe('search-only');
  });

  it('SECURITY_ANALYST (no account.read, holds only force_logout) → search-only', () => {
    expect(accountsAccessTier(['SECURITY_ANALYST'])).toBe('search-only');
  });

  it('a role holding NONE of the four /accounts permissions → none (today\'s hidden+forbidden behaviour)', () => {
    // TENANT_BILLING_ADMIN holds only subscription.manage.
    expect(accountsAccessTier(['TENANT_BILLING_ADMIN'])).toBe('none');
    expect(accountsAccessTier(['TENANT_ADMIN'])).toBe('none');
    expect(accountsAccessTier(['ORG_ADMIN'])).toBe('none');
  });

  it('no roles at all → none', () => {
    expect(accountsAccessTier([])).toBe('none');
  });

  it('unresolved/failed GET /api/admin/me (null/undefined) fails OPEN to full — never a new degrade from an outage', () => {
    expect(accountsAccessTier(null)).toBe('full');
    expect(accountsAccessTier(undefined)).toBe('full');
  });

  it('an unknown/custom role is treated as holding account.read (fail open) → full, not search-only', () => {
    expect(accountsAccessTier(['SOME_FUTURE_ROLE'])).toBe('full');
  });

  it('bite: a role holding ONLY account.lock among the three non-read keys still gets search-only (OR, not AND)', () => {
    // No seed role holds lock alone, but the function must not require ALL
    // three — it is an OR over [lock, unlock, force_logout].
    expect(accountsAccessTier(['SUPPORT_LOCK'])).toBe('search-only');
  });
});
