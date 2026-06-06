import Link from 'next/link';
import { getAuditListState, AuditScreen } from '@/features/audit';

export const dynamic = 'force-dynamic';

/**
 * IAM audit + security read parity route (TASK-PC-FE-003 — ADR-MONO-013
 * Phase 2 slice 2). An in-console nav destination (NOT a catalog product —
 * the catalog `iam.baseRoute` stays `/accounts`, FE-002 unchanged).
 *
 * Server component: the initial audit page is fetched server-side via the
 * IAM admin-service client with the HttpOnly operator token + active
 * tenant (`getAuditListState()`). READ-ONLY — no mutation. Resilience is
 * handled there:
 *   - 401 → `redirect('/login')` (clean re-login, no partial authed state).
 *   - no active tenant → a "select a tenant" gate (never an empty
 *     `X-Tenant-Id`).
 *   - 403 PERMISSION_DENIED (incl. the intersection-permission rule) /
 *     403 TENANT_SCOPE_DENIED / 422 → an inline, actionable,
 *     non-crashing state.
 *   - 503 / timeout → a degraded notice; the console shell stays intact
 *     (the `(console)` layout still renders around this).
 */
export default async function AuditPage() {
  const state = await getAuditListState({ page: 0, size: 20 });

  if (state.noTenant) {
    return (
      <section aria-labelledby="audit-heading">
        <h1 id="audit-heading" className="mb-6 text-2xl font-semibold">
          감사 · 보안 조회
        </h1>
        <div
          role="status"
          data-testid="audit-no-tenant"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          <p className="mb-2 font-medium text-foreground">
            테넌트를 먼저 선택하세요.
          </p>
          <p>
            감사·보안 조회는 테넌트 범위로 수행됩니다. 상단의 테넌트
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

  if (state.permissionError) {
    return (
      <section aria-labelledby="audit-heading">
        <h1 id="audit-heading" className="mb-6 text-2xl font-semibold">
          감사 · 보안 조회
        </h1>
        <div
          role="status"
          data-testid="audit-permission-denied"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          {state.permissionError.code === 'TENANT_SCOPE_DENIED'
            ? '선택한 테넌트에 대한 감사 조회 권한이 없습니다.'
            : '이 조회를 수행할 권한이 없습니다. (로그인 이력·의심 활동은 보안 이벤트 조회 권한이 추가로 필요합니다.)'}
        </div>
      </section>
    );
  }

  if (state.degraded || !state.page) {
    return (
      <section aria-labelledby="audit-heading">
        <h1 id="audit-heading" className="mb-6 text-2xl font-semibold">
          감사 · 보안 조회
        </h1>
        <div
          role="status"
          data-testid="audit-degraded"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          감사 서비스를 일시적으로 불러올 수 없습니다. 콘솔의 다른 기능은
          계속 사용할 수 있습니다. 잠시 후 다시 시도하세요.
        </div>
      </section>
    );
  }

  return <AuditScreen initial={state.page} />;
}
