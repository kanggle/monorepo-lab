'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { cn } from '@/shared/lib/cn';
import { GROUPS, isParent } from './console-nav-config';
import {
  matchesRoute,
  activeHref,
  parentKeyForPath,
  navPathFor,
  PARENTS,
} from './console-nav-matching';
import { NavIcon } from './console-nav-icons';

/**
 * TASK-PC-FE-039 — Vercel-style left sidebar navigation. Moves the console
 * section links out of the top bar into a grouped left rail with an active
 * (current-route) highlight.
 *
 * TASK-PC-FE-059 — Vercel-style **drill-in**: a top-level item that has
 * submenus (a NavParent) renders as a toggle. Clicking it replaces the
 * list with that parent pinned at the very top (a back control) followed by its
 * submenu links; clicking the pinned parent drills back out to the full
 * top-level list. Leaves keep navigating directly. The current route
 * auto-opens the matching parent (deep-linking `/wms/outbound` opens WMS with
 * `출고` active). data-testids + hrefs are preserved verbatim; the only new
 * destination testid is `nav-wms-ops` (the `운영` child, formerly reached via
 * `nav-wms`).
 *
 * TASK-PC-FE-244 — data/logic/render split: the nav-tree data (`GROUPS` +
 * node types + `isParent`) now lives in `console-nav-config.ts`, and the pure
 * route-matching helpers (`matchesRoute`/`activeHref`/`parentKeyForPath`) now
 * live in `console-nav-matching.ts`. This file keeps the stateful component,
 * `leafClass`, and the chevron icons. Behavior/DOM/classes unchanged.
 *
 * TASK-PC-FE-297 — (1) every item carries a decorative icon
 * (`console-nav-icons.tsx`, `aria-hidden`, the text label stays the
 * accessible name); (2) group labels are visually subordinate to the items
 * (smaller, lighter, no uppercase tracking) — still the same `<p>` element, so
 * the accessibility tree is unchanged; (3) a drill parent whose child is the
 * current route shows as active in the top-level list too (`aria-current="true"`
 * + `data-active`) — before, collapsing the drill manually left NO marker of
 * where you were; (4) the pathname is alias-resolved (`navPathFor`) so
 * `/dashboards/health` lights up 개요.
 */

const leafClass = (active: boolean) =>
  cn(
    'flex items-center gap-2 rounded-md px-2 py-1.5 text-sm transition-colors',
    'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
    active
      ? 'bg-accent font-medium text-foreground'
      : 'text-muted-foreground hover:bg-accent hover:text-foreground',
  );

function ChevronRight() {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width="14"
      height="14"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      className="shrink-0 opacity-60"
    >
      <path d="m9 18 6-6-6-6" />
    </svg>
  );
}

function ChevronLeft() {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width="14"
      height="14"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      className="shrink-0 opacity-60"
    >
      <path d="m15 18-6-6 6-6" />
    </svg>
  );
}

export function ConsoleSidebarNav() {
  const pathname = navPathFor(usePathname() ?? '');
  // Drill state. Initialised from the route so a deep-link into a child route
  // opens its parent; re-synced on every navigation to the current route's
  // parent — or collapsed to the top-level list when the new route is NOT under
  // any parent (TASK-PC-FE-176). This is what makes the "Platform Console" brand
  // link (→ /dashboards/overview, a non-parent route) return the sidebar to the
  // top-level menu instead of stranding it in a stale WMS/ERP/… drill.
  //
  // A manual collapse or peek (clicking the pinned parent / a parent toggle)
  // sets state WITHOUT a route change, so this effect does not fire and the
  // manual state is preserved until the next actual navigation.
  const [openKey, setOpenKey] = useState<string | null>(() =>
    parentKeyForPath(pathname),
  );

  useEffect(() => {
    setOpenKey(parentKeyForPath(pathname));
  }, [pathname]);

  const openParent =
    openKey === null ? null : PARENTS.find((p) => p.key === openKey) ?? null;

  if (openParent) {
    const active = activeHref(openParent.children, pathname);
    return (
      <nav
        aria-label="콘솔 내비게이션"
        className="sticky top-14 flex flex-col gap-1 p-4"
      >
        <button
          type="button"
          data-testid={openParent.testid}
          onClick={() => setOpenKey(null)}
          aria-expanded
          className={cn(
            'flex items-center gap-1.5 rounded-md px-2 py-1.5 text-left text-sm font-medium text-foreground',
            'transition-colors hover:bg-accent',
            'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
          )}
        >
          <ChevronLeft />
          <NavIcon name={openParent.icon} />
          <span className="min-w-0 truncate">{openParent.label}</span>
        </button>
        <div className="ml-1 flex flex-col gap-0.5 border-l border-border pl-3">
          {openParent.children.map((child) => (
            <Link
              key={child.href}
              href={child.href}
              data-testid={child.testid}
              aria-current={active === child.href ? 'page' : undefined}
              className={leafClass(active === child.href)}
            >
              <NavIcon name={child.icon} />
              <span className="min-w-0 truncate">{child.label}</span>
            </Link>
          ))}
        </div>
      </nav>
    );
  }

  return (
    <nav
      aria-label="콘솔 내비게이션"
      className="sticky top-14 flex flex-col gap-6 p-4"
    >
      {GROUPS.map((group, gi) => (
        <div key={group.label ?? `g${gi}`} className="flex flex-col gap-0.5">
          {group.label && (
            <p
              data-testid={group.testid}
              className="px-2 pb-0.5 text-[11px] font-normal text-muted-foreground/70"
            >
              {group.label}
            </p>
          )}
          {group.items.map((node) => {
            if (isParent(node)) {
              // Active-state gap fix (TASK-PC-FE-297): the current route is
              // under this parent but the drill is collapsed (manual back-toggle)
              // → the parent itself carries the «you are here» marker. Not
              // `aria-current="page"` — the button is not the page; `"true"`
              // says «the current item is inside this one».
              const containsCurrent = activeHref(node.children, pathname) !== null;
              return (
                <button
                  key={node.key}
                  type="button"
                  data-testid={node.testid}
                  onClick={() => setOpenKey(node.key)}
                  aria-expanded={false}
                  aria-current={containsCurrent ? 'true' : undefined}
                  data-active={containsCurrent ? 'true' : undefined}
                  className={cn(
                    'flex items-center gap-2 rounded-md px-2 py-1.5 text-left text-sm transition-colors',
                    containsCurrent
                      ? 'bg-accent font-medium text-foreground'
                      : 'text-muted-foreground hover:bg-accent hover:text-foreground',
                    'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
                  )}
                >
                  <NavIcon name={node.icon} />
                  <span className="min-w-0 flex-1 truncate">{node.label}</span>
                  <ChevronRight />
                </button>
              );
            }
            const active = matchesRoute(pathname, node.href);
            return (
              <Link
                key={node.href}
                href={node.href}
                data-testid={node.testid}
                aria-current={active ? 'page' : undefined}
                className={leafClass(active)}
              >
                <NavIcon name={node.icon} />
                <span className="min-w-0 truncate">{node.label}</span>
              </Link>
            );
          })}
        </div>
      ))}
    </nav>
  );
}
