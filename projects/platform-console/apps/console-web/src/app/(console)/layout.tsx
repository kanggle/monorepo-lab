import type { ReactNode } from 'react';
import Link from 'next/link';
import { redirect } from 'next/navigation';
import { headers } from 'next/headers';
import {
  isAuthenticated,
  isSampleVisitor,
  getActiveTenant,
  getIdToken,
  getAccessToken,
} from '@/shared/lib/session';
import { decodeJwtPayload } from '@/shared/lib/jwt';
import { buildLoginRedirectFor } from '@/shared/lib/login-redirect';
import { getCatalog } from '@/features/catalog';
import {
  selectableTenants,
  groupTenantsByCompany,
  TenantSwitcher,
  type CompanyGroup,
  type CompanyGroupInput,
} from '@/features/tenant';
import { listOrgNodes, getOrgNodeTenants } from '@/features/org-hierarchy';
import { ThemeToggle } from '@/shared/ui/ThemeToggle';
import { AccountMenu } from '@/shared/ui/AccountMenu';
import { NotificationBell } from '@/features/notifications';
import { ConsoleSidebarNav } from '@/shared/ui/ConsoleSidebarNav';
import { ApiError } from '@/shared/api/errors';
import { DemoBackendNotice } from '@/widgets/demo-notice/DemoBackendNotice';
import { DemoHeartbeat } from '@/widgets/demo-heartbeat/DemoHeartbeat';
import { SampleVisitorBanner } from '@/widgets/sample-visitor/SampleVisitorBanner';
import { SampleScreenNotice } from '@/widgets/sample-visitor/SampleScreenNotice';

/**
 * The signed-in operator's display identity for the account menu. Read
 * verification-free from the IAM OIDC `id_token` (falling back to the access
 * token, then a generic label) — DISPLAY ONLY, never an authorization input
 * (§ 2.1; the `id_token` is not a credential). TASK-PC-FE-041.
 */
function accountDisplayLabel(idToken: string | null, accessToken: string | null): string {
  const claims = decodeJwtPayload(idToken) ?? decodeJwtPayload(accessToken);
  const pick = (k: string): string | null => {
    const v = claims?.[k];
    return typeof v === 'string' && v.trim() !== '' ? v : null;
  };
  return pick('email') ?? pick('preferred_username') ?? pick('sub') ?? '운영자';
}

export const dynamic = 'force-dynamic';

/**
 * Builds the login redirect URL, preserving the intended destination via
 * `?redirect=<path>` (Gap D / F6 — TASK-PC-FE-115).
 *
 * Reads the current path from the `x-pathname` header injected by
 * middleware.ts and hands it to the sanitiser.
 *
 * 🔴 **규칙 자체는 여기 없다** — `shared/lib/login-redirect.ts` 로 뺐다
 * (`TASK-PC-FE-280`). 서버 컴포넌트 안의 **비-export 함수**였던 탓에, 그것을 지키는
 * 테스트(`tests/unit/layout-login-redirect.test.ts`)가 로직을 **로컬에 재구현**해
 * 두고 그 재구현을 검사했다 — 진짜 함수는 계산에 **한 번도 안 들어갔다**. 이제 정의는 하나고,
 * 테스트와 제품이 **같은 함수**를 쓴다.
 */
async function buildLoginRedirect(): Promise<string> {
  const hdrs = await headers();
  return buildLoginRedirectFor(hdrs.get('x-pathname'));
}

/**
 * Console shell layout (Vercel-style — TASK-PC-FE-039).
 *
 * Layout: a full-width sticky top bar holds only the brand + the **tenant
 * switcher** (+ theme toggle / account controls); the section navigation lives
 * in a **left sidebar** ({@link ConsoleSidebarNav}). The sidebar is
 * `hidden md:block` (desktop ops console; a mobile drawer is a deferred
 * follow-up — the top bar controls stay visible on all sizes).
 *
 * =============================================================================
 * 🔴🔴 The guard below changed meaning (ADR-MONO-074 — TASK-PC-FE-282)
 * =============================================================================
 * It used to say «an anonymous visitor cannot come in». It now says «an
 * anonymous visitor comes in, but cannot reach a backend»:
 *
 *   - authenticated operator ({@link isAuthenticated}) → this shell, real data,
 *     exactly as before;
 *   - sample visitor ({@link isSampleVisitor} — BOTH session cookies absent) →
 *     this shell, sample data: every backend call site answers from the sample
 *     router (`shared/api/sample-gate.ts`), and the fetch allow-list guard
 *     (`tests/unit/sample-fetch-allowlist.test.ts`) keeps a new call site from
 *     appearing outside that branch;
 *   - a half session (access cookie only / operator cookie only) → `/login`,
 *     exactly as before.
 *
 * The sample shell (A7): the account menu slot is a «로그인» link carrying
 * `?redirect=<current path>`; the tenant switcher shows the single read-only
 * sample tenant (it falls out of the sample registry); the notification bell
 * reads the sample inbox; the persistent banner is rendered HERE, not per page;
 * `DemoHeartbeat` and `DemoBackendNotice` are NOT mounted — an anonymous tab
 * must not keep the demo EC2 instance alive (ADR-MONO-071 D8), and a sample
 * screen does not depend on the backend, so «the demo is off» would be a false
 * warning.
 *
 * Registry unavailable here does NOT blank the shell — the switcher simply
 * has no options; the catalog page renders its own degraded state
 * (integration-heavy resilience; console-integration-contract § 2.5).
 */
export default async function ConsoleLayout({
  children,
}: {
  children: ReactNode;
}) {
  const sampleVisitor = await isSampleVisitor();
  if (!sampleVisitor && !(await isAuthenticated())) redirect(await buildLoginRedirect());

  const activeTenant = await getActiveTenant();
  const accountLabel = sampleVisitor
    ? null
    : accountDisplayLabel(await getIdToken(), await getAccessToken());
  const sampleLoginHref = sampleVisitor ? await buildLoginRedirect() : null;
  let tenants: string[] = [];
  try {
    const catalog = await getCatalog();
    tenants = selectableTenants(catalog.products);
  } catch (err) {
    if (err instanceof ApiError && err.status === 401)
      redirect(await buildLoginRedirect());
    tenants = []; // degraded — switcher hidden, shell still usable
  }

  // Org-node (company) grouping for the tenant switcher (TASK-PC-FE-237 /
  // ADR-047). This CANNOT break the shell: any failure (403 for a
  // non-`org.manage` operator, 503, timeout) falls back to `companies = []`,
  // i.e. the flat switcher — unchanged behaviour.
  //
  // Only ROOT nodes (`parentId === null`) become companies, and we fetch each
  // root's subtree tenants (`getOrgNodeTenants`). This bounds the extra
  // requests to the number of ROOT companies, not the number of nodes: the
  // discovery `listOrgNodes()` call plus one tenant call per root. With zero
  // org nodes there are no roots, so the per-company fan-out is zero (the
  // net-zero property — the world as it is today). A per-root failure drops
  // just that company and keeps going.
  let companies: CompanyGroup[] = [];
  try {
    const list = await listOrgNodes();
    const roots = list.items.filter((n) => n.parentId === null);
    const groups: CompanyGroupInput[] = [];
    for (const root of roots) {
      try {
        const subtree = await getOrgNodeTenants(root.orgNodeId);
        groups.push({
          orgNodeId: root.orgNodeId,
          name: root.name,
          tenantIds: subtree.tenantIds,
        });
      } catch {
        // drop just this company — keep the rest of the switcher intact
      }
    }
    companies = groupTenantsByCompany(tenants, groups).companies;
  } catch {
    companies = []; // flat switcher — unchanged behaviour
  }

  return (
    <div className="flex min-h-screen flex-col">
      {sampleVisitor ? (
        <SampleVisitorBanner />
      ) : (
        <>
          {/* TASK-MONO-585 AC-3 — 인증된 66개 화면 전부가 이 셸 안에 있다. 데모가 꺼져
              있으면 여섯 도메인의 데이터가 통째로 비는데, 그때 화면은 Vercel 에서 멀쩡히
              뜬다 ⇒ 말하지 않으면 "고장" 으로 읽힌다. 인스턴스가 켜져 있는데 한 도메인만
              죽은 경우는 이 배너가 아니라 `/dashboards/health` 가 말한다(위젯 헤더 참조). */}
          <DemoBackendNotice />
          {/* 🔴🔴 데모 인스턴스 keep-alive 핑거 — **로그인한 운영자에게만** 마운트한다.
              샘플 방문자(ADR-MONO-074)는 이 셸에 들어오지만 이 분기를 타지 않는다 —
              익명 방문자의 열린 탭이 EC2 예산을 태우면 안 된다(ADR-MONO-071 D8).
              (마운트 지점만 믿지 않는다: 라우트 핸들러가 서버에서 세션을 다시 확인한다 —
              `api/demo/heartbeat/route.ts`.) */}
          <DemoHeartbeat />
        </>
      )}
      <header className="sticky top-0 z-40 border-b border-border bg-background/80 backdrop-blur supports-[backdrop-filter]:bg-background/60">
        <div className="flex h-14 items-center justify-between px-4 sm:px-6 lg:px-8">
          <Link
            href="/dashboards/overview"
            className="rounded-sm text-sm font-semibold tracking-tight text-foreground transition-colors hover:text-foreground/80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            Platform Console
          </Link>
          <div className="flex items-center gap-3">
            <TenantSwitcher
              tenants={tenants}
              activeTenant={activeTenant}
              companies={companies}
            />
            <NotificationBell />
            <ThemeToggle />
            {sampleLoginHref !== null ? (
              <Link
                href={sampleLoginHref}
                prefetch={false}
                data-testid="sample-visitor-login"
                className="rounded-md border border-border px-3 py-1.5 text-sm font-medium text-foreground hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              >
                로그인
              </Link>
            ) : (
              <AccountMenu accountLabel={accountLabel ?? '운영자'} />
            )}
          </div>
        </div>
      </header>
      <div className="flex flex-1">
        <aside className="hidden w-56 shrink-0 border-r border-border md:block">
          <ConsoleSidebarNav />
        </aside>
        <main className="min-w-0 flex-1 px-4 py-8 sm:px-6 lg:px-8">
          <div className="mx-auto max-w-6xl">
            {sampleVisitor ? <SampleScreenNotice /> : null}
            {children}
          </div>
        </main>
      </div>
    </div>
  );
}
