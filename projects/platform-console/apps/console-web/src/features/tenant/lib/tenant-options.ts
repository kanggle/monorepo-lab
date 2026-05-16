import type { RegistryProduct } from '@/shared/api/registry-types';

/**
 * Derives the operator's selectable tenant set from the registry response.
 *
 * GAP already scopes the registry to the operator's tenant scope
 * (console-registry-api.md § Tenant selection rule / § Multi-tenant
 * isolation) — a single-tenant operator only ever sees their own slug, a
 * platform operator sees all registered+ACTIVE tenants. The console simply
 * unions the `tenants` of available products; it never widens this set
 * (multi-tenant M4 — no client-side enumeration).
 */
export function selectableTenants(products: RegistryProduct[]): string[] {
  const set = new Set<string>();
  for (const p of products) {
    if (!p.available) continue;
    for (const t of p.tenants) set.add(t);
  }
  return [...set].sort();
}
