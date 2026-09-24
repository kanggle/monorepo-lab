import { GROUPS, isParent, type NavLeaf, type NavParent, type NavNode } from './console-nav-config';

/**
 * TASK-PC-FE-244 — pure route-matching helpers, split out of
 * `ConsoleSidebarNav.tsx`. Framework-agnostic (no React import) so they are
 * independently unit-testable — see `tests/unit/console-nav-matching.test.ts`.
 */

const ALL_NODES: NavNode[] = GROUPS.flatMap((g) => g.items);
export const PARENTS: NavParent[] = ALL_NODES.filter(isParent);

/**
 * TASK-PC-FE-297 — routes that have no nav entry of their own but belong
 * under one: the pathname is matched AS IF it were the target.
 *
 * `/dashboards/health` (도메인 상태) is deliberately NOT a nav entry
 * (TASK-PC-FE-068 — it is reached only from the 개요 page's «도메인 상태 요약»
 * card, and carries a back link to 개요). Before this alias, landing on it
 * lit up NOTHING in the sidebar. It is the drill-down of 개요, so 개요 is the
 * item that should read as «you are here». Keeping the alias HERE, not as a
 * nav leaf, preserves PC-FE-068 (`domain-health-nav.test.tsx` pins that the
 * nav config carries no `/dashboards/health` literal).
 *
 * `/account` (계정 설정) is intentionally NOT aliased — see TASK-PC-FE-297's
 * implementation record: it is the signed-in operator's own settings, reached
 * from the top-bar account menu (AWS/GCP place it there too), and none of the
 * sidebar sections is its parent; lighting up any item would be a false
 * «you are here».
 */
export const NAV_ROUTE_ALIASES: Readonly<Record<string, string>> = {
  '/dashboards/health': '/dashboards/overview',
};

/** The pathname the sidebar should match against (alias-resolved). */
export function navPathFor(pathname: string): string {
  for (const [from, to] of Object.entries(NAV_ROUTE_ALIASES)) {
    if (pathname === from || pathname.startsWith(`${from}/`)) return to;
  }
  return pathname;
}

export function matchesRoute(pathname: string, href: string): boolean {
  // `/console` (catalog) is an exact match — the catalog root must not light up
  // on sub-pages; everything else is prefix-matched at a path boundary.
  if (href === '/console') return pathname === '/console';
  return pathname === href || pathname.startsWith(`${href}/`);
}

/**
 * The single active leaf among a set, by **longest** matching href, so a parent
 * route (`/wms`) does NOT also light up on a nested child route
 * (`/wms/outbound`) — only the most specific match wins.
 */
export function activeHref(leaves: NavLeaf[], pathname: string): string | null {
  let best: string | null = null;
  for (const leaf of leaves) {
    if (matchesRoute(pathname, leaf.href)) {
      if (best === null || leaf.href.length > best.length) best = leaf.href;
    }
  }
  return best;
}

/** The parent whose child matches the current route, or null. */
export function parentKeyForPath(pathname: string): string | null {
  for (const parent of PARENTS) {
    if (activeHref(parent.children, pathname) !== null) return parent.key;
  }
  return null;
}
