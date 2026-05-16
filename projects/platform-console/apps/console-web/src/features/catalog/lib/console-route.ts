import type { RegistryProduct } from '@/shared/api/registry-types';

/**
 * Resolves a registry product to its concrete console-internal landing
 * route.
 *
 * The registry's `baseRoute` is the product's *logical* console prefix
 * (GAP-owned, data-driven — adding a product never needs console code). The
 * *which screen that prefix lands on* is a console-internal concern owned
 * here. For `gap` the Phase-2 operator surface (TASK-PC-FE-002) is the
 * accounts parity screen, so the GAP tile resolves to `/accounts`
 * (console-integration-contract § 2.4.1; ADR-MONO-013 § 3 parity "accounts").
 *
 * Every other product falls through to its registry `baseRoute` unchanged
 * (wms/scm/erp/finance — built in later phases) — so this stays additive and
 * data-driven; no product list is hardcoded beyond the one Phase-2 binding.
 */
export function resolveConsoleRoute(product: RegistryProduct): string {
  if (product.productKey === 'gap') return '/accounts';
  return product.baseRoute;
}
