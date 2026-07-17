import { GROUPS, isParent, type NavLeaf, type NavParent, type NavNode } from './console-nav-config';

/**
 * TASK-PC-FE-244 — pure route-matching helpers, split out of
 * `ConsoleSidebarNav.tsx`. Framework-agnostic (no React import) so they are
 * independently unit-testable — see `tests/unit/console-nav-matching.test.ts`.
 */

const ALL_NODES: NavNode[] = GROUPS.flatMap((g) => g.items);
export const PARENTS: NavParent[] = ALL_NODES.filter(isParent);

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
