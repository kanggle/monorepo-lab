import { describe, it, expect } from 'vitest';
import {
  RegistryResponseSchema,
  RegistryProductSchema,
} from '@/shared/api/registry-types';

/**
 * Contract test — the typed registry schema MUST accept the exact response
 * envelope documented in the authoritative producer contract
 * `iam/specs/contracts/http/console-registry-api.md`
 * (GET /api/admin/console/registry 200 OK example) and reject shape drift.
 */

// Verbatim from console-registry-api.md § Response 200 OK.
const PRODUCER_EXAMPLE = {
  products: [
    {
      productKey: 'gap',
      displayName: 'Global Account Platform',
      available: true,
      tenants: ['fan-platform', 'wms', 'scm'],
      baseRoute: '/gap',
    },
    {
      productKey: 'wms',
      displayName: 'Warehouse Management Platform',
      available: true,
      tenants: ['wms'],
      baseRoute: '/wms',
    },
    {
      productKey: 'scm',
      displayName: 'Supply Chain Management Platform',
      available: true,
      tenants: ['scm'],
      baseRoute: '/scm',
    },
    {
      productKey: 'erp',
      displayName: 'Enterprise Resource Planning',
      available: false,
      tenants: [],
      baseRoute: '/erp',
    },
    {
      productKey: 'finance',
      displayName: 'Finance Platform',
      available: false,
      tenants: [],
      baseRoute: '/finance',
    },
  ],
};

describe('registry contract (console-registry-api.md envelope)', () => {
  it('parses the verbatim producer 200 example', () => {
    const parsed = RegistryResponseSchema.parse(PRODUCER_EXAMPLE);
    expect(parsed.products).toHaveLength(5);
    expect(parsed.products.map((p) => p.productKey)).toEqual([
      'gap',
      'wms',
      'scm',
      'erp',
      'finance',
    ]);
    const erp = parsed.products.find((p) => p.productKey === 'erp')!;
    expect(erp.available).toBe(false);
    expect(erp.tenants).toEqual([]);
  });

  it('rejects an unknown productKey (catalog membership is fixed)', () => {
    expect(() =>
      RegistryProductSchema.parse({
        productKey: 'crm',
        displayName: 'CRM',
        available: true,
        tenants: [],
        baseRoute: '/crm',
      }),
    ).toThrow();
  });

  it('rejects a missing required field (envelope drift surfaces)', () => {
    expect(() =>
      RegistryProductSchema.parse({
        productKey: 'gap',
        displayName: 'GAP',
        available: true,
        tenants: ['wms'],
        // baseRoute missing
      }),
    ).toThrow();
  });
});
