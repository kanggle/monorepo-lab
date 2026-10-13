import { describe, it, expect } from 'vitest';
import { registryTenantSuggestions } from '@/shared/lib/registry-tenant-suggestions';
import type { RegistryProduct } from '@/shared/api/registry-types';

/**
 * `registryTenantSuggestions` (TASK-PC-FE-336) — the operator-groups tenant
 * suggestion rule: union of `tenants` of `available` products, sorted,
 * `'*'` excluded (unlike `features/tenant/lib/tenant-options.ts
 * selectableTenants`, which keeps `'*'` for the switcher).
 */

function product(p: Partial<RegistryProduct>): RegistryProduct {
  return {
    productKey: 'iam',
    displayName: 'IAM',
    available: true,
    tenants: [],
    baseRoute: '/accounts',
    ...p,
  } as RegistryProduct;
}

describe('registryTenantSuggestions', () => {
  it('unions tenants across available products, sorted', () => {
    const out = registryTenantSuggestions([
      product({ tenants: ['beta-corp', 'acme-corp'] }),
      product({ productKey: 'wms', tenants: ['acme-corp', 'gamma-inc'] }),
    ]);
    expect(out).toEqual(['acme-corp', 'beta-corp', 'gamma-inc']);
  });

  it('excludes `*` (unlike the tenant-switcher rule)', () => {
    const out = registryTenantSuggestions([
      product({ tenants: ['*', 'acme-corp'] }),
    ]);
    expect(out).toEqual(['acme-corp']);
  });

  it('ignores unavailable products', () => {
    const out = registryTenantSuggestions([
      product({ available: false, tenants: ['hidden-corp'] }),
      product({ productKey: 'wms', tenants: ['visible-corp'] }),
    ]);
    expect(out).toEqual(['visible-corp']);
  });

  it('empty input → empty output', () => {
    expect(registryTenantSuggestions([])).toEqual([]);
  });
});
