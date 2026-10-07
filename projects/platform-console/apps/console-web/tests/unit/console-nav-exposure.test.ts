import { describe, it, expect } from 'vitest';
import {
  isHrefHiddenFor,
  subscriptionBadgeKeys,
  defaultSubscriptionBadgeKeys,
} from '@/shared/ui/console-nav-exposure';
import { GROUPS } from '@/shared/ui/console-nav-config';
import type { DomainKey } from '@/shared/guide/permission-map';

/**
 * TASK-PC-FE-314 — pure-function unit tests for the exposure gate, independent
 * of the React rendering layer (`sidebar-role-subscription.test.tsx` covers
 * the rendered component). AC-0/1/2/3/5 citations below mirror the task's
 * Acceptance Criteria.
 */
describe('isHrefHiddenFor — admin / admin-per-card gate (AC-1/AC-2/AC-5)', () => {
  it('AC-0 ②: undefined/null roles (unresolved or failed /me) never hide anything', () => {
    expect(isHrefHiddenFor('/tenants', undefined)).toBe(false);
    expect(isHrefHiddenFor('/tenants', null)).toBe(false);
  });

  it('AC-1: TENANT_ADMIN + TENANT_BILLING_ADMIN lack tenant.manage/org.manage → /tenants, /org-hierarchy hidden', () => {
    const roles = ['TENANT_ADMIN', 'TENANT_BILLING_ADMIN'];
    expect(isHrefHiddenFor('/tenants', roles)).toBe(true);
    expect(isHrefHiddenFor('/org-hierarchy', roles)).toBe(true);
  });

  it('AC-1: the same combo HOLDS subscription.manage (TB)/partnership.manage (TA)/operator.manage (TA) → visible', () => {
    const roles = ['TENANT_ADMIN', 'TENANT_BILLING_ADMIN'];
    expect(isHrefHiddenFor('/subscriptions', roles)).toBe(false);
    expect(isHrefHiddenFor('/partnerships', roles)).toBe(false);
    expect(isHrefHiddenFor('/operators', roles)).toBe(false);
  });

  it('AC-2: SUPER_ADMIN holds every admin permission EXCEPT partnership.manage (rbac.md:112, permission-map.ts documented mismatch)', () => {
    const roles = ['SUPER_ADMIN'];
    expect(isHrefHiddenFor('/tenants', roles)).toBe(false);
    expect(isHrefHiddenFor('/org-hierarchy', roles)).toBe(false);
    expect(isHrefHiddenFor('/subscriptions', roles)).toBe(false);
    expect(isHrefHiddenFor('/operators', roles)).toBe(false);
    expect(isHrefHiddenFor('/audit', roles)).toBe(false);
    expect(isHrefHiddenFor('/partnerships', roles)).toBe(true);
  });

  it('AC-2: ORG_ADMIN sees 조직 계층 (org.manage) but not 테넌트 (no tenant.manage)', () => {
    const roles = ['ORG_ADMIN'];
    expect(isHrefHiddenFor('/org-hierarchy', roles)).toBe(false);
    expect(isHrefHiddenFor('/tenants', roles)).toBe(true);
  });

  it('AC-5: an unknown/custom role is never treated as lacking a permission (fail open)', () => {
    expect(isHrefHiddenFor('/tenants', ['SOME_FUTURE_ROLE'])).toBe(false);
    expect(isHrefHiddenFor('/partnerships', ['SOME_FUTURE_ROLE'])).toBe(false);
    // union: ONE unknown role among several known-lacking roles still wins.
    expect(
      isHrefHiddenFor('/tenants', ['TENANT_ADMIN', 'SOME_FUTURE_ROLE']),
    ).toBe(false);
  });

  it('admin-per-card: visible if ANY listed permission is held, hidden only if none are', () => {
    // /iam gates on {operator.manage, account.read, audit.read}; TENANT_ADMIN
    // holds only operator.manage.
    expect(isHrefHiddenFor('/iam', ['TENANT_ADMIN'])).toBe(false);
    // TENANT_BILLING_ADMIN holds none of the three.
    expect(isHrefHiddenFor('/iam', ['TENANT_BILLING_ADMIN'])).toBe(true);
  });

  it('domain-gated hrefs are NEVER hidden by role, regardless of roles', () => {
    expect(isHrefHiddenFor('/wms', [])).toBe(false);
    expect(isHrefHiddenFor('/wms', ['SUPPORT_LOCK'])).toBe(false);
  });

  it('public/operator gates are always visible', () => {
    expect(isHrefHiddenFor('/guide', [])).toBe(false);
    expect(isHrefHiddenFor('/dashboards/overview', [])).toBe(false);
  });
});

describe('subscriptionBadgeKeys — domain subscription badge (AC-0 ③/AC-3)', () => {
  it('AC-0 ③: no active tenant (undefined) ⇒ no badge anywhere', () => {
    const keys = subscriptionBadgeKeys(GROUPS, undefined);
    expect(keys.size).toBe(0);
  });

  it('AC-3: zero subscriptions ⇒ every domain parent is badged', () => {
    const keys = defaultSubscriptionBadgeKeys(new Set<DomainKey>());
    expect(keys.has('wms')).toBe(true);
    expect(keys.has('scm')).toBe(true);
    expect(keys.has('finance')).toBe(true);
    expect(keys.has('erp')).toBe(true);
    expect(keys.has('ecommerce')).toBe(true);
  });

  it('AC-3: a subscribed domain is NOT badged; an unsubscribed one is (control)', () => {
    const keys = defaultSubscriptionBadgeKeys(new Set<DomainKey>(['wms']));
    expect(keys.has('wms')).toBe(false);
    expect(keys.has('scm')).toBe(true);
  });

  it('every domain parent subscribed ⇒ no badges', () => {
    const keys = defaultSubscriptionBadgeKeys(
      new Set<DomainKey>(['wms', 'scm', 'finance', 'erp', 'ecommerce', 'fan']),
    );
    expect(keys.size).toBe(0);
  });
});
