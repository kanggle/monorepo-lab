import Link from 'next/link';
import {
  getOperatorGroupsState,
  OperatorGroupsScreen,
} from '@/features/operator-groups';
import { getGrantableRolesOrNull } from '@/features/operators/api/operators-api';

export const dynamic = 'force-dynamic';

/**
 * ADR-MONO-046 운영자 그룹 route (TASK-PC-FE-250) — the IAM ▸ 운영자 그룹 menu
 * destination, replacing the TASK-PC-FE-225 stub. Groups bundle operators into
 * a named unit and bulk-grant roles / tenant-assignments (fan-out, D2-A), gated
 * by `group.manage` (SUPER_ADMIN, self-tenant TENANT_ADMIN, subtree ORG_ADMIN).
 *
 * Server component: the initial group page is fetched server-side via the IAM
 * admin-service client with the HttpOnly operator token + active tenant
 * (`getOperatorGroupsState()`). Resilience is handled there (mirrors
 * `org-hierarchy/page.tsx`):
 *   - 401 → `redirect('/login')` (inside `getOperatorGroupsState`).
 *   - no active tenant → a "select a tenant" gate (a SUPER_ADMIN selects `*`).
 *   - 403 (lacks `group.manage`) → an inline "group.manage required" notice.
 *   - 503 / timeout → a degraded notice; the console shell stays intact.
 *
 * The group-grant role picker is pre-filtered by the caller's grantable roles
 * (`getGrantableRolesOrNull` — the SAME operator convention; no group-specific
 * grantable-roles endpoint exists). Fired concurrently with the list state to
 * avoid an SSR waterfall; already fail-graceful (never throws) so a failure
 * resolves to `null` and the screen falls back to the full role set (the
 * producer 403 stays the authoritative no-escalation gate).
 */
export default async function OperatorGroupsPage() {
  const statePromise = getOperatorGroupsState();
  const grantableRolesPromise = getGrantableRolesOrNull();
  const state = await statePromise;

  if (state.noTenant) {
    return (
      <section aria-labelledby="operator-groups-heading">
        <h1 id="operator-groups-heading" className="mb-6 text-2xl font-semibold">
          운영자 그룹
        </h1>
        <div
          role="status"
          data-testid="operator-groups-no-tenant"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          <p className="mb-2 font-medium text-foreground">
            테넌트를 먼저 선택하세요.
          </p>
          <p>
            운영자 그룹 관리는 활성 테넌트가 선택되어 있어야 합니다. 상단의
            테넌트 스위처에서 테넌트(SUPER_ADMIN 은 플랫폼 스코프 *)를 선택한 뒤
            다시 시도하세요.
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
      <section aria-labelledby="operator-groups-heading">
        <h1 id="operator-groups-heading" className="mb-6 text-2xl font-semibold">
          운영자 그룹
        </h1>
        <div
          role="status"
          data-testid="operator-groups-permission-denied"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          {state.permissionError.code === 'TENANT_SCOPE_DENIED'
            ? '선택한 테넌트에 대한 운영자 그룹 관리 권한이 없습니다.'
            : '운영자 그룹 관리는 group.manage 권한이 필요합니다 (SUPER_ADMIN, 자기 테넌트 TENANT_ADMIN, 서브트리 ORG_ADMIN). 이 화면을 조회·변경할 권한이 없습니다.'}
        </div>
      </section>
    );
  }

  if (state.degraded || !state.page) {
    return (
      <section aria-labelledby="operator-groups-heading">
        <h1 id="operator-groups-heading" className="mb-6 text-2xl font-semibold">
          운영자 그룹
        </h1>
        <div
          role="status"
          data-testid="operator-groups-degraded"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          운영자 그룹 서비스를 일시적으로 불러올 수 없습니다. 콘솔의 다른 기능은
          계속 사용할 수 있습니다. 잠시 후 다시 시도하세요.
        </div>
      </section>
    );
  }

  const grantableRoles = await grantableRolesPromise;

  return (
    <OperatorGroupsScreen initial={state.page} grantableRoles={grantableRoles} />
  );
}
