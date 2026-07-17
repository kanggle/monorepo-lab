import { describe, it, expect } from 'vitest';
import {
  matchesRoute,
  activeHref,
  parentKeyForPath,
} from '@/shared/ui/console-nav-matching';
import type { NavLeaf } from '@/shared/ui/console-nav-config';

/**
 * TASK-PC-FE-244 — isolated unit coverage for the pure route-matching
 * helpers split out of `ConsoleSidebarNav.tsx` into `console-nav-matching.ts`.
 * Cases are based on the CURRENT behavior read from the source (no new
 * semantics): `matchesRoute` is exact-or-prefix-at-a-path-boundary except
 * `/console`, which is exact-only; `activeHref` picks the single **longest**
 * matching leaf so a nested child route wins over its parent route;
 * `parentKeyForPath` resolves the real `GROUPS` nav tree (imported
 * transitively via `console-nav-matching.ts`'s `PARENTS`).
 */

describe('matchesRoute', () => {
  it('exact match', () => {
    expect(matchesRoute('/wms', '/wms')).toBe(true);
  });

  it('nested/prefix match at a path boundary', () => {
    expect(matchesRoute('/wms/outbound', '/wms')).toBe(true);
  });

  it('rejects a same-prefix sibling that is not a path-boundary match', () => {
    // '/wmsx' starts with 'wms' as a raw string but is NOT under '/wms/' —
    // matchesRoute requires the boundary slash, not a bare string prefix.
    expect(matchesRoute('/wmsx', '/wms')).toBe(false);
  });

  it('rejects an unrelated route', () => {
    expect(matchesRoute('/scm', '/wms')).toBe(false);
  });

  it('/console (catalog) is an exact match — sub-pages do not light it up', () => {
    expect(matchesRoute('/console', '/console')).toBe(true);
    expect(matchesRoute('/console/catalog', '/console')).toBe(false);
  });
});

describe('activeHref', () => {
  const leaves: NavLeaf[] = [
    { href: '/wms', label: '개요', testid: 't-overview' },
    { href: '/wms/outbound', label: '출고', testid: 't-outbound' },
    { href: '/wms/inventory', label: '재고', testid: 't-inventory' },
  ];

  it('returns the single exact match', () => {
    expect(activeHref(leaves, '/wms/inventory')).toBe('/wms/inventory');
  });

  it('picks the LONGEST match when a nested route also matches its parent leaf', () => {
    // '/wms/outbound' matches both the '/wms' leaf (prefix) and the
    // '/wms/outbound' leaf (exact) — the more specific one wins.
    expect(activeHref(leaves, '/wms/outbound')).toBe('/wms/outbound');
  });

  it('falls back to the parent leaf on a nested path with no exact leaf', () => {
    expect(activeHref(leaves, '/wms/outbound/detail')).toBe('/wms/outbound');
  });

  it('returns null when nothing matches', () => {
    expect(activeHref(leaves, '/scm')).toBeNull();
  });
});

describe('parentKeyForPath', () => {
  it('resolves a deep-linked child route to its owning parent key', () => {
    expect(parentKeyForPath('/wms/outbound')).toBe('wms');
  });

  it('resolves the parent-owned root child (e.g. /iam) to its parent key', () => {
    expect(parentKeyForPath('/iam')).toBe('iam');
  });

  it('resolves a nested grandchild-style path under a parent to its parent key', () => {
    expect(parentKeyForPath('/iam/guide')).toBe('iam');
  });

  it('returns null for a top-level (non-parent) leaf route', () => {
    expect(parentKeyForPath('/dashboards/overview')).toBeNull();
  });

  it('returns null for the root path (no leaf/parent owns "/")', () => {
    expect(parentKeyForPath('/')).toBeNull();
  });

  it('returns null for an unrecognized route', () => {
    expect(parentKeyForPath('/no-such-route')).toBeNull();
  });
});
