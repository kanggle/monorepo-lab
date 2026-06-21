import { describe, it, expect } from 'vitest';
import { selectableTenants } from '@/features/tenant/lib/tenant-options';
import type { RegistryProduct } from '@/shared/api/registry-types';

/**
 * TASK-PC-FE-116 — derive the operator's selectable tenant set from the
 * registry. multi-tenant M4: the console only UNIONS available products'
 * tenants (dedup + sort); it never includes an unavailable product's tenants
 * and never widens the set with client-side enumeration.
 */

function product(p: Partial<RegistryProduct>): RegistryProduct {
  return {
    productKey: 'iam',
    displayName: 'IAM',
    available: true,
    tenants: [],
    baseRoute: '/accounts',
    ...p,
  };
}

describe('selectableTenants', () => {
  it('returns an empty array for no products', () => {
    expect(selectableTenants([])).toEqual([]);
  });

  it('unions tenants across available products', () => {
    const result = selectableTenants([
      product({ productKey: 'iam', tenants: ['acme', 'globex'] }),
      product({ productKey: 'wms', tenants: ['initech'] }),
    ]);
    expect(result).toEqual(['acme', 'globex', 'initech']);
  });

  it('dedups tenants shared across products', () => {
    const result = selectableTenants([
      product({ productKey: 'iam', tenants: ['acme', 'globex'] }),
      product({ productKey: 'wms', tenants: ['acme'] }),
    ]);
    expect(result).toEqual(['acme', 'globex']);
  });

  it('sorts the resulting set lexicographically', () => {
    const result = selectableTenants([
      product({ productKey: 'iam', tenants: ['zeta', 'alpha', 'mid'] }),
    ]);
    expect(result).toEqual(['alpha', 'mid', 'zeta']);
  });

  it('excludes tenants of unavailable products (M4 isolation)', () => {
    const result = selectableTenants([
      product({ productKey: 'iam', available: true, tenants: ['acme'] }),
      product({ productKey: 'erp', available: false, tenants: ['hidden'] }),
    ]);
    expect(result).toEqual(['acme']);
  });

  it('returns an empty array when every product is unavailable', () => {
    const result = selectableTenants([
      product({ productKey: 'erp', available: false, tenants: ['hidden'] }),
    ]);
    expect(result).toEqual([]);
  });
});
