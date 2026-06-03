import Link from 'next/link';
import { getOperatorsListState, OperatorsScreen } from '@/features/operators';
import { getSelfOperatorIdOrNull } from '@/features/operators/api/operators-api';
import { getCatalog } from '@/features/catalog';
import { selectableTenants } from '@/features/tenant';

export const dynamic = 'force-dynamic';

/**
 * GAP operators-management parity route (TASK-PC-FE-004 — ADR-MONO-013
 * Phase 2 slice 3). An in-console nav destination (NOT a catalog product —
 * the catalog `gap.baseRoute` stays `/accounts`, FE-002 unchanged).
 *
 * Server component: the initial operators page is fetched server-side via
 * the GAP admin-service client with the HttpOnly operator token + active
 * tenant (`getOperatorsListState()`). Resilience is handled there:
 *   - 401 → `redirect('/login')` (clean re-login, no partial authed state).
 *   - no active tenant → a "select a tenant" gate (never an empty
 *     `X-Tenant-Id`).
 *   - 403 PERMISSION_DENIED (not SUPER_ADMIN / lacks operator.manage) /
 *     403 TENANT_SCOPE_DENIED → an inline "not permitted" state (no crash,
 *     no re-login loop). The `/operators` nav entry is best-effort gated
 *     in the layout when derivable; the server 403 is ALWAYS handled here.
 *   - 503 / timeout → a degraded notice; the console shell stays intact
 *     (the `(console)` layout still renders around this).
 *
 * The create-form tenant options come from the operator's own (GAP-scoped)
 * registry response — GAP enforces the operator's tenant scope
 * producer-side; the `*` platform sentinel only appears when the operator
 * is platform-scope, so the UI never offers `*` to a non-platform operator
 * (task Edge Case; the producer is the final authority anyway).
 */
export default async function OperatorsPage() {
  const state = await getOperatorsListState({ page: 0, size: 20 });

  if (state.noTenant) {
    return (
      <section aria-labelledby="operators-heading">
        <h1
          id="operators-heading"
          className="mb-6 text-2xl font-semibold"
        >
          운영자 관리
        </h1>
        <div
          role="status"
          data-testid="operators-no-tenant"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          <p className="mb-2 font-medium text-foreground">
            테넌트를 먼저 선택하세요.
          </p>
          <p>
            운영자 관리는 테넌트 범위로 수행됩니다. 상단의 테넌트
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
      <section aria-labelledby="operators-heading">
        <h1
          id="operators-heading"
          className="mb-6 text-2xl font-semibold"
        >
          운영자 관리
        </h1>
        <div
          role="status"
          data-testid="operators-permission-denied"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          {state.permissionError.code === 'TENANT_SCOPE_DENIED'
            ? '선택한 테넌트에 대한 운영자 관리 권한이 없습니다.'
            : '운영자 관리는 SUPER_ADMIN(operator.manage 권한)만 수행할 수 있습니다.'}
        </div>
      </section>
    );
  }

  if (state.degraded || !state.page) {
    return (
      <section aria-labelledby="operators-heading">
        <h1
          id="operators-heading"
          className="mb-6 text-2xl font-semibold"
        >
          운영자 관리
        </h1>
        <div
          role="status"
          data-testid="operators-degraded"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          운영자 서비스를 일시적으로 불러올 수 없습니다. 콘솔의 다른 기능은
          계속 사용할 수 있습니다. 잠시 후 다시 시도하세요.
        </div>
      </section>
    );
  }

  // Create-form tenant options (operator-scoped, from the registry). The
  // `*` platform sentinel only appears for a platform-scope operator; we
  // pass it as the `isPlatformOperator` hint and keep it OUT of the normal
  // tenant dropdown (the form re-adds `*` only when platform-scope).
  //
  // TASK-PC-FE-045: the SELF profile (`operatorContext.defaultAccountId`) +
  // password moved to 계정 설정(`/account`); this page is 남 관리 only.
  let tenantOptions: string[] = [];
  let isPlatformOperator = false;
  try {
    const catalog = await getCatalog();
    const tenants = selectableTenants(catalog.products);
    isPlatformOperator = tenants.includes('*');
    tenantOptions = tenants.filter((t) => t !== '*');
  } catch {
    // Registry unavailable here does NOT block operators management — the
    // create form simply has no preset tenant options (the operator can
    // still manage existing operators; the producer is authoritative).
    tenantOptions = [];
    isPlatformOperator = false;
  }

  // TASK-PC-FE-020 — resolve the caller's own operatorId so OperatorsScreen
  // can disable the per-row "프로파일 편집" button on the self row. The helper
  // is fail-graceful (any failure → null) — the producer
  // 400 SELF_PROFILE_UPDATE_FORBIDDEN_VIA_ADMIN_PATH is the authoritative
  // gate; this is the UX layer.
  const selfOperatorId = await getSelfOperatorIdOrNull();

  return (
    <OperatorsScreen
      initial={state.page}
      tenantOptions={tenantOptions}
      isPlatformOperator={isPlatformOperator}
      selfOperatorId={selfOperatorId}
    />
  );
}
