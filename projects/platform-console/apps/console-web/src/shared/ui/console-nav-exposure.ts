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

/* ───────────────────────────────────────────────────────────────────────────
 * TASK-PC-FE-338 c-2 — the per-menu «업무 권한 없음» signal + the write-button gate
 * (console-integration-contract § 2.4.3.4 «Domain nav — two signals» and «Read-only sets hide
 * write buttons»; owner decision 2026-10-15: per menu).
 *
 * INPUT = row 12 (`GET /api/admin/me/domain-access`) ∩ the active tenant's subscribed domains
 * ({@link businessRolesWithin}). After P3 (TASK-BE-632) that set EQUALS the assumed token's
 * `roles` — so the signal predicts the domain server's verdict without ever decoding the token
 * (contract Failure Scenario 1).
 *
 * DATA = `permission-map.ts` `domain` gates: `serverRequires` (primary read) / `writeRequires`
 * (mutation buttons). A leaf the server opens on subscription alone declares nothing and is
 * never signalled — measured per leaf in the ticket's c-2 AC-0 table. P5–P8 (TASK-MONO-792~795)
 * add data, not rules.
 *
 * 🔴 Every «don't know» (row 12 failed, no active tenant, registry failed, sample visitor) is
 *    `undefined` here and means NO badge, buttons SHOWN — an outage must never read as «no
 *    permission»; the server stays the gate.
 * ─────────────────────────────────────────────────────────────────────────── */

/** One row-12 role (`GET /api/admin/me/domain-access` `roles[]`). */
export interface MyDomainRole {
  domainKey: string;
  role: string;
}

/**
 * Row 12 ∩ subscribed domains. `undefined` = don't know (row 12 failed, or no subscription data);
 * an EMPTY set = known to hold no business role on a subscribed domain.
 */
export function businessRolesWithin(
  rows: readonly MyDomainRole[] | null | undefined,
  subscribedDomains: ReadonlySet<DomainKey> | undefined,
): ReadonlySet<string> | undefined {
  if (!rows || subscribedDomains === undefined) return undefined;
  const subscribed = subscribedDomains as ReadonlySet<string>;
  return new Set(rows.filter((r) => subscribed.has(r.domainKey)).map((r) => r.role));
}

function domainGateOf(href: string) {
  const gate = GATE_BY_HREF.get(href);
  return gate?.kind === 'domain' ? gate : null;
}

/** Holds at least one of `required`? An absent/empty requirement is always satisfied. */
function holdsAny(
  myBusinessRoles: ReadonlySet<string>,
  required: readonly string[] | undefined,
): boolean {
  if (!required || required.length === 0) return true;
  return required.some((r) => myBusinessRoles.has(r));
}

/** Is this leaf refused by the server for lack of a business role? (subscribed domain only) */
function leafLacksBusinessPermission(
  href: string,
  subscribedDomains: ReadonlySet<DomainKey>,
  myBusinessRoles: ReadonlySet<string>,
): boolean {
  const gate = domainGateOf(href);
  if (!gate || !gate.serverRequires || gate.serverRequires.length === 0) return false;
  // «구독 필요» wins — an unsubscribed domain is never ALSO «업무 권한 없음».
  if (!subscribedDomains.has(gate.domain)) return false;
  return !holdsAny(myBusinessRoles, gate.serverRequires);
}

/**
 * The nav identifiers carrying «업무 권한 없음»: leaf `href`s, plus a drill parent's `key` when
 * EVERY `domain`-gated child of it is badged (one cause per item — a parent with an open child
 * is not «no permission»).
 */
export function businessPermissionBadgeKeys(
  groups: readonly NavGroup[],
  subscribedDomains: ReadonlySet<DomainKey> | undefined,
  myBusinessRoles: ReadonlySet<string> | undefined,
): ReadonlySet<string> {
  const out = new Set<string>();
  if (subscribedDomains === undefined || myBusinessRoles === undefined) return out;

  for (const g of groups) {
    for (const n of g.items) {
      if (isParent(n)) {
        const domainChildren = n.children.filter((c) => domainGateOf(c.href) !== null);
        let all = domainChildren.length > 0;
        for (const c of domainChildren) {
          if (leafLacksBusinessPermission(c.href, subscribedDomains, myBusinessRoles)) {
            out.add(c.href);
          } else {
            all = false;
          }
        }
        if (all) out.add(n.key);
      } else if (leafLacksBusinessPermission(n.href, subscribedDomains, myBusinessRoles)) {
        out.add(n.href);
      }
    }
  }
  return out;
}

/** {@link businessPermissionBadgeKeys} over the full, unfiltered nav tree. */
export function defaultBusinessPermissionBadgeKeys(
  subscribedDomains: ReadonlySet<DomainKey> | undefined,
  myBusinessRoles: ReadonlySet<string> | undefined,
): ReadonlySet<string> {
  return businessPermissionBadgeKeys(GROUPS, subscribedDomains, myBusinessRoles);
}

/**
 * AC-2 — may the screen at `href` show its mutation buttons? `false` only when the operator's
 * business roles are KNOWN and hold none of the screen's `writeRequires`. Unknown roles, or a
 * screen with no declaration → `true` (the server stays final).
 */
export function canWriteOn(
  href: string,
  myBusinessRoles: ReadonlySet<string> | undefined,
): boolean {
  if (myBusinessRoles === undefined) return true;
  return holdsAny(myBusinessRoles, domainGateOf(href)?.writeRequires);
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
