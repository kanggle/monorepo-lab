import { describe, it, expect } from 'vitest';
import { selectableTenants } from '@/features/tenant';
import type { RegistryProduct } from '@/shared/api/registry-types';

/**
 * Multi-tenant isolation regression (rules/traits/multi-tenant.md M4/M6).
 *
 * The console must NEVER widen the operator's tenant set beyond what the
 * (GAP-scoped) registry returned. A single-tenant operator's derived
 * selectable set must contain only their own slug — no enumeration of other
 * tenants. Cross-tenant write rejection itself is enforced server-side in
 * `/api/tenant` + by IAM (covered by the route + producer regression test);
 * this guards the client-side derivation that feeds the switcher.
 */
describe('multi-tenant isolation — selectableTenants', () => {
  it('a single-tenant operator only sees their own slug', () => {
    // IAM already scoped this response to operator tenant 'wms'.
    const products: RegistryProduct[] = [
      {
        productKey: 'iam',
        displayName: 'IAM',
        available: true,
        tenants: ['wms'],
        baseRoute: '/iam',
      },
      {
        productKey: 'wms',
        displayName: 'WMS',
        available: true,
        tenants: ['wms'],
        baseRoute: '/wms',
      },
    ];
    expect(selectableTenants(products)).toEqual(['wms']);
  });

  it('never derives tenants from an unavailable product', () => {
    const products: RegistryProduct[] = [
      {
        productKey: 'erp',
        displayName: 'ERP',
        available: false,
        // Even if a leaked slug appeared here it must be ignored.
        tenants: ['other-tenant'],
        baseRoute: '/erp',
      },
    ];
    expect(selectableTenants(products)).toEqual([]);
  });

  it('a platform operator sees the full registered set, de-duplicated', () => {
    const products: RegistryProduct[] = [
      {
        productKey: 'iam',
        displayName: 'IAM',
        available: true,
        tenants: ['fan-platform', 'wms', 'scm'],
        baseRoute: '/iam',
      },
      {
        productKey: 'wms',
        displayName: 'WMS',
        available: true,
        tenants: ['wms'],
        baseRoute: '/wms',
      },
    ];
    expect(selectableTenants(products)).toEqual([
      'fan-platform',
      'scm',
      'wms',
    ]);
  });
});
