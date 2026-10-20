import { getOperatorsListState, OperatorsScreen } from '@/features/operators';
import { getGrantableRolesOrNull } from '@/features/operators/api/operators-api';
import { getCatalog } from '@/features/catalog';
import { getSelfOrNull } from '@/shared/api/iam-operators-read';
import { hasPermission } from '@/shared/guide/permission-map';
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
 * TASK-PC-FE-354 — whether the caller is platform-scope comes from
 * `GET /api/admin/me` (`homeTenantId === '*'` + `operator.manage`, the
 * `new-tenant/page.tsx` precedent). It used to be «`*` among the registry's
 * selectable tenants», which is never true (`*` is not a switcher entry), so
 * the platform create form had never rendered. The same flag gates the
 * «모든 테넌트» list view and the 업무 권한 dialog's `'*'` row. The direct
 * create form is platform-scope (`*`) only; a company operator is INVITED
 * into the active tenant. The producer is the final authority either way.
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
  // TASK-PC-FE-354 — the catalog is now read only for `subscribedDomains`
  // below, which awaits it only when an active tenant resolved. Mark a
  // rejection handled up-front so that branch skipping it cannot leave an
  // unhandled rejection (the `await` below still sees the error).
  catalogPromise.catch(() => undefined);
  // TASK-PC-FE-354 — ONE `GET /api/admin/me` gives both the self id and the
  // platform-scope judgement (was `getSelfOperatorIdOrNull`, the same call
  // reduced to `operatorId`). Fail-graceful: any failure → `null`.
  const selfPromise = getSelfOrNull();
  // feat/iam-grantable-roles-filter — the create / edit-roles role
  // checkboxes are pre-filtered to what THIS operator may grant. Fired
  // concurrently with catalog/self (same waterfall-avoidance posture,
  // TASK-PC-FE-118); already fail-graceful (`getGrantableRolesOrNull`
  // never throws) so no separate try/catch is needed here — a failure
  // resolves to `null`, and the screen falls back to the full role set.
  const grantableRolesPromise = getGrantableRolesOrNull();

  // TASK-PC-FE-020 — resolve the caller's own operatorId so OperatorsScreen
  // can disable the per-row "프로파일 편집" button on the self row. The helper
  // is fail-graceful (any failure → null) — the producer
  // 400 SELF_PROFILE_UPDATE_FORBIDDEN_VIA_ADMIN_PATH is the authoritative
  // gate; this is the UX layer. Started up-front (concurrently with the
  // catalog fetch above) and awaited here. (TASK-PC-FE-118)
  const me = await selfPromise;
  const selfOperatorId = me?.operatorId ?? null;

  // TASK-PC-FE-354 — platform-scope caller ⇒ the `*`-only «플랫폼 운영자
  // 등록» form, the «모든 테넌트» list view and the 업무 권한 dialog's '*' row.
  // Mirrors `new-tenant/page.tsx` (`homeTenantId === '*'` + the permission
  // this screen needs). An absent `homeTenantId` (optional in the schema) or
  // a failed `/me` ⇒ false (fail-closed for the UI; the producer decides).
  const isPlatformOperator =
    me !== null &&
    me.homeTenantId === '*' &&
    hasPermission(me.roles, 'operator.manage');

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

  // TASK-PC-FE-338 slice b — ADR-MONO-083 「업무 권한」 dialog's "구독 없음"
  // marking source, resolved HERE (the `app/` layer) the SAME way
  // `(console)/layout.tsx` resolves it for the sidebar badge and
  // `(console)/domain-permission-sets/page.tsx` resolves it for the create
  // form (`features/catalog` `getCatalog()` ∩ active tenant). A registry
  // failure (or no active tenant) leaves it `null` — "don't know ⇒ don't
  // mark"; the already-caught `catalogPromise` above is reused rather than
  // fetching the catalog twice.
  let subscribedDomains: string[] | null = null;
  if (activeTenant) {
    try {
      const catalog = await catalogPromise;
      subscribedDomains = catalog.products
        .filter((p) => p.tenants.includes(activeTenant))
        .map((p) => p.productKey)
        .filter((k) => k !== 'iam');
    } catch {
      subscribedDomains = null;
    }
  }

  return (
    <OperatorsScreen
      initial={state.page}
      isPlatformOperator={isPlatformOperator}
      selfOperatorId={selfOperatorId}
      activeTenant={activeTenant}
      grantableRoles={grantableRoles}
      subscribedDomains={subscribedDomains}
    />
  );
}
