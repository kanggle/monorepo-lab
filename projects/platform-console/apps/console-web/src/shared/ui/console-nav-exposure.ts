/**
 * TASK-PC-FE-314 — the sidebar exposure gate: which nav items an operator's
 * ROLES hide, and which nav items get a «구독 필요» badge because the ACTIVE
 * tenant has not subscribed to that domain.
 *
 * Kept OUT of `console-nav-config.ts` (TASK-PC-FE-314 task constraint —
 * another in-flight PR reorders that file's IAM drill children, so this
 * module's whole job is to not enlarge that diff). Pure (no React / framework
 * import) like every other `console-nav-*` module — independently unit
 * testable, and importable from a Server Component (`(console)/layout.tsx`)
 * without pulling React into the bundle twice.
 *
 * Joins the EXISTING sources — never a hand-written per-menu permission list
 * (task Failure Scenario 3: a second copy drifts from `permission-map.ts`):
 *   - `permission-map.ts` `PERMISSION_MAP` — the href → `gate` table (already
 *     the single source `tests/unit/permission-map-drift.test.ts` pins against
 *     the nav tree).
 *   - `permission-map.ts` `hasPermission()` — the role × permission seed
 *     table (rbac.md copy, `RBAC_SEED_MATRIX`/`RBAC_ROLES`) lookup, shared
 *     with that module's own `accountsAccessTier()` (TASK-PC-FE-326).
 *   - the caller's roles (`GET /api/admin/me` `roles[]`, promoted to
 *     `shared/api/iam-operators-read.ts` — see that module's
 *     `getSelfRolesOrNull`) and the active tenant's subscribed domains (the
 *     registry catalog the `(console)` layout already fetches), both passed
 *     in as plain data — this module touches neither `next/headers` nor any
 *     `features/*` import (`shared/` may import `shared/` only,
 *     architecture.md § Allowed Dependencies).
 */
import {
  GROUPS,
  isParent,
  type NavGroup,
} from './console-nav-config';
import {
  PERMISSION_MAP,
  hasPermission,
  type DomainKey,
  type PermissionGate,
} from '@/shared/guide/permission-map';

const GATE_BY_HREF: ReadonlyMap<string, PermissionGate> = new Map(
  PERMISSION_MAP.map((row) => [row.href, row.gate]),
);

// AC-0 ① — `hasPermission` (imported above) is the single source for "does
// ANY of the caller's roles hold this permission" (fails OPEN on an unknown
// role — the task Goal's own principle: hiding is a screen convenience, the
// server is the real gate). TASK-PC-FE-326 moved the implementation into
// `permission-map.ts` so `accountsAccessTier()` there can reuse it too,
// rather than this module keeping its own copy.

/**
 * TASK-PC-FE-314 AC-0 ①② — whether `href` should be hidden for a caller whose
 * roles are `myRoles`.
 *
 * `myRoles` is `null`/`undefined` in TWO cases this function treats
 * identically (never hide): `GET /api/admin/me` failed (AC-0 ②, «실패 시
 * 지금처럼 전부 보임» — an outage must never read as "no permission"), or the
 * caller simply didn't pass anything (every pre-existing render path / test).
 *
 * `domain`-gated hrefs are NEVER hidden by this function — the task Goal
 * table draws a hard line between the two gate kinds: `admin`/`admin-per-card`
 * hide, `domain` only badges ({@link subscriptionBadgeKeys}). `public`/
 * `operator` are always visible, same as before this task.
 *
 * A missing permission-map row (should not happen — the drift test guards
 * it) fails OPEN (not hidden), for the same "don't know ⇒ don't hide" reason.
 */
export function isHrefHiddenFor(
  href: string,
  myRoles: readonly string[] | null | undefined,
): boolean {
  if (!myRoles) return false;
  const gate = GATE_BY_HREF.get(href);
  if (!gate) return false;
  switch (gate.kind) {
    case 'admin':
      return !hasPermission(myRoles, gate.permission);
    case 'admin-per-card':
      return !gate.permissions.some((p) => hasPermission(myRoles, p));
    case 'public':
    case 'operator':
    case 'domain':
      return false;
  }
}

/**
 * TASK-PC-FE-314 AC-0 ③ / Goal table «domain 게이트» row — the set of nav
 * node identifiers (a `NavParent.key`, or a flat `NavLeaf.href` for a domain
 * leaf that is not inside a drill parent — none exist today, but the rule is
 * generic) that should carry a «구독 필요» badge: EVERY child of that node
 * gates on the SAME domain, and the active tenant has not subscribed to it.
 *
 * `subscribedDomains === undefined` ⇒ no active tenant (or the registry call
 * failed) ⇒ returns an empty set — AC-0 ③ is explicit that "no active
 * tenant" must not badge anything (a tenant-picker prompt already exists;
 * doubling it with a subscription warning would mislead). A registry failure
 * is folded into the same "don't know ⇒ don't badge" posture (the task has
 * no AC for this edge case; recorded as a deliberate extension in the task's
 * Implementation Notes).
 *
 * A parent with a MIXED child domain set (none exist today — every drill
 * parent is single-domain) is left un-badged: badging it would misname which
 * domain is missing, and the per-screen guidance each child already carries
 * (task Out of Scope) is the honest fallback.
 */
export function subscriptionBadgeKeys(
  groups: readonly NavGroup[],
  subscribedDomains: ReadonlySet<DomainKey> | undefined,
): ReadonlySet<string> {
  const out = new Set<string>();
  if (subscribedDomains === undefined) return out;

  const domainOf = (href: string): DomainKey | null => {
    const gate = GATE_BY_HREF.get(href);
    return gate?.kind === 'domain' ? gate.domain : null;
  };

  for (const g of groups) {
    for (const n of g.items) {
      if (isParent(n)) {
        const domains = new Set(
          n.children.map((c) => domainOf(c.href)).filter((d): d is DomainKey => d !== null),
        );
        if (domains.size === 1) {
          const [domain] = domains;
          if (!subscribedDomains.has(domain)) out.add(n.key);
        }
      } else {
        const domain = domainOf(n.href);
        if (domain !== null && !subscribedDomains.has(domain)) out.add(n.href);
      }
    }
  }
  return out;
}

/** Re-exported for callers (`ConsoleSidebarNav`, the `(console)` layout) that
 *  only need the type, not the rest of `permission-map.ts`'s surface. */
export type { DomainKey };

/**
 * The GROUPS this module computes badges over by default — always the FULL,
 * unfiltered tree (not whatever `visibleGroups()` returned), because a
 * `domain`-gated child is never hidden by {@link isHrefHiddenFor}, so the two
 * are equivalent for today's config — but computing over the raw tree keeps
 * that true BY CONSTRUCTION rather than by coincidence if a future role ever
 * did gate a domain child.
 */
export function defaultSubscriptionBadgeKeys(
  subscribedDomains: ReadonlySet<DomainKey> | undefined,
): ReadonlySet<string> {
  return subscriptionBadgeKeys(GROUPS, subscribedDomains);
}
