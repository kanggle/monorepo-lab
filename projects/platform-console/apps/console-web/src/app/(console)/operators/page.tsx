import { getOperatorsListState, OperatorsScreen } from '@/features/operators';
import {
  getSelfOperatorIdOrNull,
  getGrantableRolesOrNull,
} from '@/features/operators/api/operators-api';
import { getCatalog } from '@/features/catalog';
import { selectableTenants } from '@/features/tenant';
import { getActiveTenant } from '@/shared/lib/session';
import { NoTenantNotice } from '@/widgets/no-tenant-notice';

export const dynamic = 'force-dynamic';

/**
 * IAM operators-management parity route (TASK-PC-FE-004 — ADR-MONO-013
 * Phase 2 slice 3). An in-console nav destination (NOT a catalog product —
 * the catalog `iam.baseRoute` stays `/accounts`, FE-002 unchanged).
 *
 * Server component: the initial operators page is fetched server-side via
 * the IAM admin-service client with the HttpOnly operator token + active
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
 * The operator's own (GAP-scoped) registry response tells whether the
 * operator is platform-scope (`*` among its tenants). TASK-MONO-772 S5 — that
 * flag is now the ONLY thing the catalog is read for: the direct create form
 * is platform-scope (`*`) only, and a company operator is INVITED into the
 * active tenant (no tenant picker any more). The producer is the final
 * authority either way.
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
        {await NoTenantNotice({
          testId: 'operators-no-tenant',
          description: (
            <>
              운영자 관리는 테넌트 범위로 수행됩니다. 상단의 테넌트
              스위처에서 테넌트를 선택한 뒤 다시 시도하세요.
            </>
          ),
        })}
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
            : '운영자 관리는 operator.manage 권한이 필요합니다 (SUPER_ADMIN 또는 자기 테넌트 TENANT_ADMIN).'}
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

  // Platform-scope hint (operator-scoped, from the registry): `*` among the
  // operator's tenants ⇒ the `*`-only «플랫폼 운영자 등록» form is offered
  // (TASK-MONO-772 S5 — company operators are invited, not created).
  //
  // TASK-PC-FE-045: the SELF profile (`operatorContext.defaultAccountId`) +
  // password moved to 계정 설정(`/account`); this page is 남 관리 only.
  // TASK-PC-FE-118 — the create-form tenant options (catalog) and the caller's
  // own operatorId (self) are independent of each other and both needed only
  // on this success path (past the noTenant/permissionError/degraded gates
  // above). Fire them concurrently to remove the SSR waterfall: page latency
  // goes from `catalog + self` to `max(catalog, self)`. They are awaited
  // individually (not via Promise.all) so the catalog try/catch keeps its
  // "registry down → empty options, non-blocking" semantics without taking the
  // independent self result down with it.
  const catalogPromise = getCatalog();
  const selfPromise = getSelfOperatorIdOrNull();
  // feat/iam-grantable-roles-filter — the create / edit-roles role
  // checkboxes are pre-filtered to what THIS operator may grant. Fired
  // concurrently with catalog/self (same waterfall-avoidance posture,
  // TASK-PC-FE-118); already fail-graceful (`getGrantableRolesOrNull`
  // never throws) so no separate try/catch is needed here — a failure
  // resolves to `null`, and the screen falls back to the full role set.
  const grantableRolesPromise = getGrantableRolesOrNull();

  let isPlatformOperator = false;
  try {
    const catalog = await catalogPromise;
    isPlatformOperator = selectableTenants(catalog.products).includes('*');
  } catch {
    // Registry unavailable here does NOT block operators management — the
    // platform create form simply stays hidden (fail-closed for the UI; the
    // invite form + list do not depend on it; the producer is authoritative).
    isPlatformOperator = false;
  }

  // TASK-PC-FE-020 — resolve the caller's own operatorId so OperatorsScreen
  // can disable the per-row "프로파일 편집" button on the self row. The helper
  // is fail-graceful (any failure → null) — the producer
  // 400 SELF_PROFILE_UPDATE_FORBIDDEN_VIA_ADMIN_PATH is the authoritative
  // gate; this is the UX layer. Started up-front (concurrently with the
  // catalog fetch above) and awaited here. (TASK-PC-FE-118)
  const selfOperatorId = await selfPromise;

  // feat/iam-grantable-roles-filter — `null` on any failure (fail-graceful
  // wrapper) ⇒ OperatorsScreen falls back to the full KNOWN_OPERATOR_ROLES
  // set (never an empty checkbox list; the producer 403 stays authoritative).
  const grantableRoles = await grantableRolesPromise;

  // TASK-PC-FE-157 — the active tenant slug drives the tenant-assignment
  // surface (배정 / 배정 해제 target it). Past the `state.noTenant` gate above
  // an active tenant is selected; read it for the client component. Any
  // failure ⇒ null (the assignment surface simply hides — the producer is
  // authoritative on scope anyway).
  const activeTenant = await getActiveTenant().catch(() => null);

  return (
    <OperatorsScreen
      initial={state.page}
      isPlatformOperator={isPlatformOperator}
      selfOperatorId={selfOperatorId}
      activeTenant={activeTenant}
      grantableRoles={grantableRoles}
    />
  );
}
