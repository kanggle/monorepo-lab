import type { RegistryProduct } from '@/shared/api/registry-types';

/**
 * Derives the "known tenant" suggestion set from a registry response, for a
 * free-text tenant-id input that wants autocomplete WITHOUT narrowing what can
 * be typed (TASK-PC-FE-336 — operator-groups tenant fields). The underlying
 * rule is identical to `features/tenant/lib/tenant-options.ts selectableTenants`
 * (union of `tenants` of `available` products) — reimplemented here, NOT
 * imported, because `shared/` must stay import-free of `features/*`
 * (architecture.md Layered-by-Feature boundary; `features/tenant` keeps its
 * own copy for the switcher's own purposes).
 *
 * `'*'` (platform-global) is EXCLUDED here — unlike the switcher, a group can
 * never be platform-global (`GroupForm`/`GroupGrantDialog` already reject it
 * client-side), so suggesting it would be actively wrong, not just unhelpful.
 */
export function registryTenantSuggestions(products: RegistryProduct[]): string[] {
  const set = new Set<string>();
  for (const p of products) {
    if (!p.available) continue;
    for (const t of p.tenants) {
      if (t !== '*') set.add(t);
    }
  }
  return [...set].sort();
}
