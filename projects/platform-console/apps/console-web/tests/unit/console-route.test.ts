import { describe, it, expect } from 'vitest';
import { resolveConsoleRoute } from '@/features/catalog/lib/console-route';
import type { ProductKey, RegistryProduct } from '@/shared/api/registry-types';

/**
 * TASK-PC-FE-116 — registry product → console-internal landing route. Only the
 * `iam` Phase-2 binding (→ /accounts) is console-owned; every other product
 * falls through to its data-driven registry `baseRoute` unchanged (additive —
 * adding a product never needs console code).
 */

function product(productKey: ProductKey, baseRoute: string): RegistryProduct {
  return {
    productKey,
    displayName: productKey.toUpperCase(),
    available: true,
    tenants: [],
    baseRoute,
  };
}

describe('resolveConsoleRoute', () => {
  it('maps the iam product to the /accounts parity screen', () => {
    expect(resolveConsoleRoute(product('iam', '/iam'))).toBe('/accounts');
  });

  it('passes every non-iam product through to its registry baseRoute', () => {
    const cases: Array<[ProductKey, string]> = [
      ['wms', '/wms'],
      ['scm', '/scm'],
      ['erp', '/erp'],
      ['finance', '/finance'],
      ['ecommerce', '/ecommerce'],
    ];
    for (const [key, route] of cases) {
      expect(resolveConsoleRoute(product(key, route))).toBe(route);
    }
  });
});
