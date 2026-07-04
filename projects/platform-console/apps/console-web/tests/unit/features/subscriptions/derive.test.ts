import { describe, it, expect } from 'vitest';
import { deriveDomainSubscriptions } from '@/features/subscriptions/lib/derive';
import type { RegistryProduct } from '@/shared/api/registry-types';

/**
 * `deriveDomainSubscriptions` — catalog → per-domain subscription state
 * (TASK-PC-FE-183 / ADR-MONO-023). A domain is ACTIVE ⟺ an `available`
 * product for that key lists the active tenant; SUSPENDED/CANCELLED drop out
 * of the catalog so they read as NOT_SUBSCRIBED.
 */

function product(over: Partial<RegistryProduct>): RegistryProduct {
  return {
    productKey: 'wms',
    displayName: 'WMS',
    available: true,
    tenants: [],
    baseRoute: '/wms',
    operatorContext: undefined,
    ...over,
  };
}

describe('deriveDomainSubscriptions', () => {
  it('returns all 5 subscribable domains (never iam) in canonical order', () => {
    const rows = deriveDomainSubscriptions([], 'acme-corp');
    expect(rows.map((r) => r.key)).toEqual([
      'wms',
      'scm',
      'finance',
      'erp',
      'ecommerce',
    ]);
  });

  it('marks a domain ACTIVE when the active tenant is in its product.tenants', () => {
    const rows = deriveDomainSubscriptions(
      [product({ productKey: 'wms', tenants: ['acme-corp', 'other'] })],
      'acme-corp',
    );
    expect(rows.find((r) => r.key === 'wms')?.state).toBe('ACTIVE');
    expect(rows.find((r) => r.key === 'scm')?.state).toBe('NOT_SUBSCRIBED');
  });

  it('marks NOT_SUBSCRIBED when the tenant is absent, the product is unavailable, or the domain is missing', () => {
    const rows = deriveDomainSubscriptions(
      [
        product({ productKey: 'wms', tenants: ['other-tenant'] }),
        product({ productKey: 'scm', available: false, tenants: ['acme-corp'] }),
      ],
      'acme-corp',
    );
    expect(rows.find((r) => r.key === 'wms')?.state).toBe('NOT_SUBSCRIBED');
    expect(rows.find((r) => r.key === 'scm')?.state).toBe('NOT_SUBSCRIBED');
    expect(rows.find((r) => r.key === 'erp')?.state).toBe('NOT_SUBSCRIBED');
  });

  it('prefers the registry displayName, falling back to the static label', () => {
    const rows = deriveDomainSubscriptions(
      [product({ productKey: 'finance', displayName: 'Finance Suite', tenants: ['acme-corp'] })],
      'acme-corp',
    );
    expect(rows.find((r) => r.key === 'finance')?.label).toBe('Finance Suite');
    // wms not in catalog → static fallback label.
    expect(rows.find((r) => r.key === 'wms')?.label).toBe('WMS (창고관리)');
  });

  it('never marks a domain ACTIVE for a non-matching tenant (isolation)', () => {
    const rows = deriveDomainSubscriptions(
      [product({ productKey: 'ecommerce', tenants: ['tenant-a'] })],
      'tenant-b',
    );
    expect(rows.find((r) => r.key === 'ecommerce')?.state).toBe('NOT_SUBSCRIBED');
  });
});
