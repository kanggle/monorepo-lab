import Link from 'next/link';
import { redirect } from 'next/navigation';
import {
  DomainHealthScreen,
  getDomainHealthState,
} from '@/features/domain-health';

export const dynamic = 'force-dynamic';

/**
 * Phase 7 "Domain Health Overview" cross-domain dashboard route
 * (TASK-PC-FE-013 — `console-integration-contract.md` § 2.4.9.2).
 *
 * The SECOND concrete `§ 2.4.9.X` composition route consumed by the
 * console. Surfaces each domain's public Spring Boot `/actuator/health`
 * status (`UP` / `DOWN` / `OUT_OF_SERVICE` / `UNKNOWN`) wrapped in the
 * per-leg `ok` / `degraded` outcome (D5.A discipline).
 *
 * Server component. The initial envelope is composed server-side by the
 * BFF (the proxy route forwards 2 headers from `shared/lib/session` to
 * console-bff — Authorization + X-Tenant-Id, NOT X-Operator-Token);
 * per-card degrade lives INSIDE the 200 payload, so the page never
 * branches on per-card status — only on the three whole-fan-out outcomes:
 *
 *   - 401 on the BFF call → `redirect('/login')` (no partial authed state).
 *   - 400 NO_ACTIVE_TENANT → render the "select a tenant" gate
 *     (X-Tenant-Id is required for log MDC / audit traceability per
 *     § 2.4.9.2 error envelope; the proxy fast-fails before the BFF call).
 *   - 502 BAD_GATEWAY → render banner-only "domain health unavailable"
 *     state; the `(console)` shell stays intact.
 */
export default async function DomainHealthPage() {
  const state = await getDomainHealthState();

  if (state.unauthorized) {
    redirect('/login');
  }

  if (state.noTenant) {
    return (
      <section aria-labelledby="domain-health-heading">
        <h1 id="domain-health-heading" className="mb-6 text-2xl font-semibold">
          도메인 상태 개요
        </h1>
        <div
          role="status"
          data-testid="domain-health-no-tenant"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          <p className="mb-2 font-medium text-foreground">
            테넌트를 먼저 선택하세요.
          </p>
          <p>
            도메인 상태 개요는 감사·관측 목적의 테넌트 컨텍스트 안에서
            제공됩니다. 상단 테넌트 스위처에서 테넌트를 선택한 뒤 다시
            시도하세요.
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

  if (state.bffUnavailable || !state.health) {
    return (
      <section aria-labelledby="domain-health-heading">
        <h1 id="domain-health-heading" className="mb-6 text-2xl font-semibold">
          도메인 상태 개요
        </h1>
        <div
          role="status"
          data-testid="domain-health-bff-unavailable"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          <p className="mb-2 font-medium text-foreground">
            도메인 상태 개요를 일시적으로 불러올 수 없습니다.
          </p>
          <p>
            콘솔 자체는 정상 동작합니다. 각 도메인 화면으로 직접 이동하거나
            잠시 후 다시 시도하세요.
          </p>
        </div>
      </section>
    );
  }

  return <DomainHealthScreen health={state.health} />;
}
