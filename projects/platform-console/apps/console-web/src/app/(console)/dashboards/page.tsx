import Link from 'next/link';
import { getOverviewState, OperatorOverviewScreen } from '@/features/dashboards';

export const dynamic = 'force-dynamic';

/**
 * GAP composed operator overview route (TASK-PC-FE-005 — ADR-MONO-013
 * Phase 2 slice 4 / ADR-MONO-015 D1-B). The console landing/home + an
 * in-console nav destination — NOT a catalog product (the catalog
 * `gap.baseRoute` stays `/accounts`, FE-002 unchanged).
 *
 * Server component: the initial overview is composed server-side by a
 * bounded fan-out over the EXISTING FE-002/003/004 read clients with the
 * HttpOnly operator token + active tenant (`getOverviewState()` →
 * `getOperatorOverview()`). READ-ONLY — no mutation, no new GAP producer,
 * NOT a Grafana embed (ADR-MONO-015 D1). Resilience:
 *   - 401 on ANY leg → `redirect('/login')` (clean WHOLE-overview
 *     re-login, no partial authed state — 401 is never a per-card degrade).
 *   - no active tenant → a "select a tenant" gate (never an empty
 *     `X-Tenant-Id` on any leg).
 *   - per-card 403 / 503 / timeout → isolated INSIDE the fan-out as a
 *     per-card status; the overview + the `(console)` shell always render
 *     the full screen with each card's own placeholder (never blank).
 */
export default async function DashboardsPage() {
  const state = await getOverviewState();

  if (state.noTenant || !state.overview) {
    return (
      <section aria-labelledby="overview-heading">
        <h1 id="overview-heading" className="mb-6 text-2xl font-semibold">
          운영자 개요
        </h1>
        <div
          role="status"
          data-testid="overview-no-tenant"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          <p className="mb-2 font-medium text-foreground">
            테넌트를 먼저 선택하세요.
          </p>
          <p>
            운영자 개요는 테넌트 범위로 구성됩니다. 상단의 테넌트
            스위처에서 테넌트를 선택한 뒤 다시 시도하세요.
          </p>
          <Link
            href="/console"
            className="mt-4 inline-block text-sm underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            카탈로그로 이동
          </Link>
        </div>
      </section>
    );
  }

  return <OperatorOverviewScreen initial={state.overview} />;
}
