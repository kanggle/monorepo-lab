import type { RegistryProduct } from '@/shared/api/registry-types';

/**
 * Derives the operator's selectable tenant set from the registry response.
 *
 * IAM already scopes the registry to the operator's tenant scope
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

/** One company (root org node) and the subtree tenants it owns. */
export interface CompanyGroupInput {
  orgNodeId: string;
  name: string;
  tenantIds: string[];
}

/** A company group as rendered in the switcher (a display-only `<optgroup>`). */
export interface CompanyGroup {
  orgNodeId: string;
  name: string;
  tenants: string[];
}

export interface GroupedTenants {
  companies: CompanyGroup[];
  ungrouped: string[];
}

/**
 * Groups the operator's selectable tenants under their owning company (root
 * org node) for the AWS-IdC-style account picker (TASK-PC-FE-237 / ADR-047).
 *
 * INVARIANTS (the correctness core — a lazy-migration `org_node_id = null`
 * tenant is a LEGAL, PERMANENT state and must NEVER disappear, D7):
 *   - only tenants present in `tenants` may appear (a group may reference a
 *     tenant the operator can't select — it is ignored);
 *   - a tenant appearing in more than one group is assigned to the FIRST
 *     matching group (deterministic, input `groups` order);
 *   - every tenant not in any group lands in `ungrouped` — the union of all
 *     company tenants + `ungrouped` EQUALS the input tenant set (no tenant is
 *     ever lost);
 *   - companies with zero matching tenants are dropped;
 *   - companies are sorted by `name`; tenants within each group and
 *     `ungrouped` are sorted alphabetically.
 */
export function groupTenantsByCompany(
  tenants: string[],
  groups: CompanyGroupInput[],
): GroupedTenants {
  const selectable = new Set(tenants);
  const assigned = new Set<string>();

  const companies: CompanyGroup[] = [];
  for (const g of groups) {
    const members: string[] = [];
    for (const t of g.tenantIds) {
      if (selectable.has(t) && !assigned.has(t)) {
        assigned.add(t);
        members.push(t);
      }
    }
    if (members.length > 0) {
      companies.push({
        orgNodeId: g.orgNodeId,
        name: g.name,
        tenants: members.slice().sort(),
      });
    }
  }
  companies.sort((a, b) => (a.name < b.name ? -1 : a.name > b.name ? 1 : 0));

  const ungrouped = tenants.filter((t) => !assigned.has(t)).slice().sort();
  return { companies, ungrouped };
}
