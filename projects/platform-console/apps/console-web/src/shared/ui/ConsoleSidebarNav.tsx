'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { cn } from '@/shared/lib/cn';

/**
 * TASK-PC-FE-039 — Vercel-style left sidebar navigation. Moves the console
 * section links out of the top bar into a grouped left rail with an active
 * (current-route) highlight. Client component for `usePathname()` active state;
 * the server `(console)` layout composes it into the shell. data-testids +
 * hrefs are preserved verbatim from the previous top-bar nav (e2e/unit
 * selectors unchanged).
 */
interface NavItem {
  href: string;
  label: string;
  testid: string;
}
interface NavGroup {
  label?: string;
  items: NavItem[];
}

const GROUPS: NavGroup[] = [
  {
    items: [
      { href: '/dashboards/overview', label: '개요', testid: 'nav-dashboards' },
      { href: '/dashboards/health', label: '도메인 상태', testid: 'nav-domain-health' },
      { href: '/console', label: '카탈로그', testid: 'nav-catalog' },
    ],
  },
  {
    label: '관리',
    items: [
      { href: '/audit', label: '감사 · 보안', testid: 'nav-audit' },
      { href: '/operators', label: '운영자 관리', testid: 'nav-operators' },
    ],
  },
  {
    label: '도메인 운영',
    items: [
      { href: '/wms', label: 'WMS', testid: 'nav-wms' },
      { href: '/scm', label: 'SCM', testid: 'nav-scm' },
      { href: '/finance', label: 'Finance', testid: 'nav-finance' },
      { href: '/erp', label: 'ERP', testid: 'nav-erp' },
    ],
  },
];

function isActive(pathname: string, href: string): boolean {
  // `/console` (catalog) is an exact match — every domain route would otherwise
  // not collide, but the catalog root must not light up on sub-pages.
  if (href === '/console') return pathname === '/console';
  return pathname === href || pathname.startsWith(`${href}/`);
}

export function ConsoleSidebarNav() {
  const pathname = usePathname() ?? '';

  return (
    <nav
      aria-label="콘솔 내비게이션"
      className="sticky top-14 flex flex-col gap-6 p-4"
    >
      {GROUPS.map((group, gi) => (
        <div key={group.label ?? `g${gi}`} className="flex flex-col gap-0.5">
          {group.label && (
            <p className="px-2 pb-1 text-xs font-medium uppercase tracking-wider text-muted-foreground">
              {group.label}
            </p>
          )}
          {group.items.map((item) => {
            const active = isActive(pathname, item.href);
            return (
              <Link
                key={item.href}
                href={item.href}
                data-testid={item.testid}
                aria-current={active ? 'page' : undefined}
                className={cn(
                  'rounded-md px-2 py-1.5 text-sm transition-colors',
                  'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
                  active
                    ? 'bg-accent font-medium text-foreground'
                    : 'text-muted-foreground hover:bg-accent hover:text-foreground',
                )}
              >
                {item.label}
              </Link>
            );
          })}
        </div>
      ))}
    </nav>
  );
}
