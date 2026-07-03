'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { cn } from '@/shared/lib/cn';

/**
 * TASK-PC-FE-039 — Vercel-style left sidebar navigation. Moves the console
 * section links out of the top bar into a grouped left rail with an active
 * (current-route) highlight.
 *
 * TASK-PC-FE-059 — Vercel-style **drill-in**: a top-level item that has
 * submenus (a {@link NavParent}) renders as a toggle. Clicking it replaces the
 * list with that parent pinned at the very top (a back control) followed by its
 * submenu links; clicking the pinned parent drills back out to the full
 * top-level list. Leaves keep navigating directly. The current route
 * auto-opens the matching parent (deep-linking `/wms/outbound` opens WMS with
 * `출고` active). data-testids + hrefs are preserved verbatim; the only new
 * destination testid is `nav-wms-ops` (the `운영` child, formerly reached via
 * `nav-wms`).
 */
interface NavLeaf {
  href: string;
  label: string;
  testid: string;
}
interface NavParent {
  key: string;
  label: string;
  testid: string;
  children: NavLeaf[];
}
type NavNode = NavLeaf | NavParent;
interface NavGroup {
  label?: string;
  items: NavNode[];
}

function isParent(node: NavNode): node is NavParent {
  return (node as NavParent).children !== undefined;
}

const GROUPS: NavGroup[] = [
  {
    items: [
      { href: '/dashboards/overview', label: '개요', testid: 'nav-dashboards' },
      // 도메인 상태(/dashboards/health) is NOT a top-level entry (TASK-PC-FE-068)
      // — it is reached only from the 개요 page's "도메인 상태 요약" card
      // "전체 보기 →" link (PC-FE-061), and that page carries a back link to the
      // overview. Keeps the top group to the 1-click home + catalog.
      { href: '/console', label: '카탈로그', testid: 'nav-catalog' },
    ],
  },
  {
    label: '관리',
    items: [
      {
        // The 3 IAM surfaces nest under one IAM drill parent: 계정 운영
        // (/accounts — the catalog IAM tile's target via resolveConsoleRoute,
        // TASK-PC-FE-062) + 감사·보안 + 운영자 관리
        // (`${IAM_ADMIN_API_BASE}/api/admin/{audit,operators}`). TASK-PC-FE-060
        // / -062 — same drill model as WMS.
        key: 'iam',
        label: 'IAM',
        testid: 'nav-iam',
        children: [
          // 개요(/iam — TASK-PC-FE-163) is the first child: a static guide to
          // the RBAC roles + per-screen access matrix + delegation chain, so an
          // operator can orient before operating. Then the two management
          // (write) surfaces in **setup-first** order — 운영자 관리 (provision
          // the operators + grant the roles that gate every other IAM surface)
          // BEFORE 계정 운영 (/accounts, the catalog IAM tile target; the
          // day-to-day end-user account CS work those operators then perform) —
          // then 감사·보안 (read-only oversight) last: orient → configure →
          // operate → review. (Catalog iam.baseRoute stays /accounts, FE-002.)
          { href: '/iam', label: '개요', testid: 'nav-iam-guide' },
          { href: '/operators', label: '운영자 관리', testid: 'nav-operators' },
          { href: '/accounts', label: '계정 운영', testid: 'nav-accounts' },
          { href: '/audit', label: '감사 · 보안', testid: 'nav-audit' },
        ],
      },
    ],
  },
  {
    label: '도메인 운영',
    items: [
      {
        key: 'wms',
        label: 'WMS',
        testid: 'nav-wms',
        children: [
          { href: '/wms', label: '개요', testid: 'nav-wms-ops' },
          { href: '/wms/outbound', label: '출고', testid: 'nav-wms-outbound' },
        ],
      },
      {
        // SCM is a drill-in parent (same model as WMS): 운영(/scm — the
        // FE-008 read section) + 보충(/scm/replenishment — the FE-077
        // replenishment operator gate) + 설정(/scm/config — the FE-080
        // seed/config operator surface: per-SKU reorder-policy + sku-supplier-map
        // upsert, the operational fix-path for the 보충 SKU_SUPPLIER_UNMAPPED
        // gap). The /scm destination lives on the 운영 child (nav-scm-ops);
        // nav-scm is the pinned parent back-toggle.
        key: 'scm',
        label: 'SCM',
        testid: 'nav-scm',
        children: [
          { href: '/scm', label: '개요', testid: 'nav-scm-ops' },
          {
            href: '/scm/replenishment',
            label: '보충',
            testid: 'nav-scm-replenishment',
          },
          { href: '/scm/config', label: '설정', testid: 'nav-scm-config' },
        ],
      },
      {
        // Finance is ONE domain (finance-platform) with TWO bound console
        // surfaces — account-service (운영: 계좌·잔액·거래) + ledger-service
        // (원장: 시산표·기간·대조, TASK-PC-FE-072). They share the finance
        // tenant gate + a single entitlement (entitled_domains ∋ finance gates
        // BOTH), so they nest under one Finance drill parent — the SAME model
        // as WMS (운영 + 출고), IAM, and ERP. TASK-PC-FE-078 (was two flat
        // sibling leaves nav-finance + nav-ledger). The parent keeps the domain
        // testid `nav-finance`; the former /finance leaf's destination is now
        // the 운영 child (`nav-finance-ops`), mirroring the WMS nav-wms →
        // nav-wms-ops move.
        key: 'finance',
        label: 'Finance',
        testid: 'nav-finance',
        children: [
          { href: '/finance', label: '운영', testid: 'nav-finance-ops' },
          { href: '/ledger', label: '원장', testid: 'nav-ledger' },
        ],
      },
      {
        // TASK-PC-FE-076 — ERP becomes a drill parent (same model as WMS):
        // the single dense `/erp` page split into 4 section routes. The
        // parent route `/erp` doubles as the first child (마스터), exactly
        // as `/wms` is WMS's 운영 child. `nav-erp` now denotes the parent
        // toggle; `nav-erp-masters` is the new child testid for `/erp`.
        key: 'erp',
        label: 'ERP',
        testid: 'nav-erp',
        children: [
          { href: '/erp', label: '마스터', testid: 'nav-erp-masters' },
          { href: '/erp/orgview', label: '통합 조회', testid: 'nav-erp-orgview' },
          { href: '/erp/approval', label: '결재함', testid: 'nav-erp-approval' },
          {
            href: '/erp/delegation',
            label: '위임',
            testid: 'nav-erp-delegation',
          },
        ],
      },
      {
        // ecommerce is a drill-in parent (same model as WMS): 운영(/ecommerce —
        // the MONO-241 health/section page) + 상품(/ecommerce/products — the
        // PC-FE-081 product operator CRUD surface, § 2.4.10) + 주문
        // (/ecommerce/orders — the PC-FE-083 order operator surface, § 2.4.10).
        // The /ecommerce destination lives on the 운영 child (nav-ecommerce-ops);
        // nav-ecommerce is the pinned parent back-toggle. Image (presigned) is a
        // later facet (PC-FE-082).
        key: 'ecommerce',
        label: 'E-Commerce',
        testid: 'nav-ecommerce',
        children: [
          { href: '/ecommerce', label: '개요', testid: 'nav-ecommerce-ops' },
          {
            href: '/ecommerce/products',
            label: '상품',
            testid: 'nav-ecommerce-products',
          },
          {
            href: '/ecommerce/orders',
            label: '주문',
            testid: 'nav-ecommerce-orders',
          },
          {
            href: '/ecommerce/shippings',
            label: '배송',
            testid: 'nav-ecommerce-shippings',
          },
          {
            href: '/ecommerce/promotions',
            label: '프로모션',
            testid: 'nav-ecommerce-promotions',
          },
          {
            href: '/ecommerce/users',
            label: '사용자',
            testid: 'nav-ecommerce-users',
          },
          {
            href: '/ecommerce/sellers',
            label: '셀러',
            testid: 'nav-ecommerce-sellers',
          },
          {
            href: '/ecommerce/notifications/templates',
            label: '알림',
            testid: 'nav-ecommerce-notifications',
          },
        ],
      },
    ],
  },
];

const ALL_NODES: NavNode[] = GROUPS.flatMap((g) => g.items);
const PARENTS: NavParent[] = ALL_NODES.filter(isParent);

function matchesRoute(pathname: string, href: string): boolean {
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
function activeHref(leaves: NavLeaf[], pathname: string): string | null {
  let best: string | null = null;
  for (const leaf of leaves) {
    if (matchesRoute(pathname, leaf.href)) {
      if (best === null || leaf.href.length > best.length) best = leaf.href;
    }
  }
  return best;
}

/** The parent whose child matches the current route, or null. */
function parentKeyForPath(pathname: string): string | null {
  for (const parent of PARENTS) {
    if (activeHref(parent.children, pathname) !== null) return parent.key;
  }
  return null;
}

const leafClass = (active: boolean) =>
  cn(
    'rounded-md px-2 py-1.5 text-sm transition-colors',
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
  const pathname = usePathname() ?? '';
  // Drill state. Initialised from the route so a deep-link into a child route
  // opens its parent; re-synced when navigation enters a different parent's
  // route. A manual collapse (clicking the pinned parent) sets null and is
  // preserved until the next navigation into a parent route.
  const [openKey, setOpenKey] = useState<string | null>(() =>
    parentKeyForPath(pathname),
  );

  useEffect(() => {
    const key = parentKeyForPath(pathname);
    if (key) setOpenKey(key);
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
          {openParent.label}
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
              {child.label}
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
            <p className="px-2 pb-1 text-xs font-medium uppercase tracking-wider text-muted-foreground">
              {group.label}
            </p>
          )}
          {group.items.map((node) => {
            if (isParent(node)) {
              return (
                <button
                  key={node.key}
                  type="button"
                  data-testid={node.testid}
                  onClick={() => setOpenKey(node.key)}
                  aria-expanded={false}
                  className={cn(
                    'flex items-center justify-between rounded-md px-2 py-1.5 text-left text-sm transition-colors',
                    'text-muted-foreground hover:bg-accent hover:text-foreground',
                    'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring',
                  )}
                >
                  <span>{node.label}</span>
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
                {node.label}
              </Link>
            );
          })}
        </div>
      ))}
    </nav>
  );
}
