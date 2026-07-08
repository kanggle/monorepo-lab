import Link from 'next/link';
import { getRbacCatalogState } from '@/shared/api/rbac-catalog';
import { PermissionsScreen } from '@/features/permissions';

export const dynamic = 'force-dynamic';

/**
 * IAM RBAC 「권한」 화면 (TASK-PC-FE-227 — TASK-PC-FE-225 스텁 대체).
 *
 * Server component: the role + permission-key catalog is fetched
 * server-side via `getRbacCatalogState()` (`shared/api/rbac-catalog.ts`,
 * TASK-BE-486 `GET /api/admin/roles` + `GET /api/admin/permissions`, both
 * gated by `operator.manage`). Resilience is handled there:
 *   - 401 → `redirect('/login')` (clean re-login, no partial authed state).
 *   - no active tenant → a "select a tenant" gate (never an empty
 *     `X-Tenant-Id` — the catalog itself is global data, but the shared
 *     gateway core still requires an active-tenant header).
 *   - 403 PERMISSION_DENIED (lacks `operator.manage`) → an inline "not
 *     permitted" state (no crash, no re-login loop).
 *   - 503 / timeout → a degraded notice; the console shell stays intact
 *     (the `(console)` layout still renders around this).
 *
 * v1 read-only — no edit affordance (Out of Scope: role/permission CRUD,
 * operator↔role assignment UI stays on the existing `/operators` screen).
 */
export default async function PermissionsPage() {
  const state = await getRbacCatalogState();

  if (state.noTenant) {
    return (
      <section aria-labelledby="permissions-heading">
        <h1 id="permissions-heading" className="mb-6 text-2xl font-semibold">
          권한
        </h1>
        <div
          role="status"
          data-testid="permissions-no-tenant"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          <p className="mb-2 font-medium text-foreground">
            테넌트를 먼저 선택하세요.
          </p>
          <p>
            권한 카탈로그 조회는 테넌트 선택 후 이용할 수 있습니다. 상단의
            테넌트 스위처에서 테넌트를 선택한 뒤 다시 시도하세요.
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
      <section aria-labelledby="permissions-heading">
        <h1 id="permissions-heading" className="mb-6 text-2xl font-semibold">
          권한
        </h1>
        <div
          role="status"
          data-testid="permissions-permission-denied"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          권한 카탈로그 조회는 operator.manage 권한이 필요합니다 (SUPER_ADMIN
          또는 자기 테넌트 TENANT_ADMIN).
        </div>
      </section>
    );
  }

  if (state.degraded || !state.roles || !state.permissions) {
    return (
      <section aria-labelledby="permissions-heading">
        <h1 id="permissions-heading" className="mb-6 text-2xl font-semibold">
          권한
        </h1>
        <div
          role="status"
          data-testid="permissions-degraded"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          권한 카탈로그 서비스를 일시적으로 불러올 수 없습니다. 콘솔의 다른
          기능은 계속 사용할 수 있습니다. 잠시 후 다시 시도하세요.
        </div>
      </section>
    );
  }

  return (
    <PermissionsScreen
      roles={state.roles}
      permissions={state.permissions}
      scope={state.scope ?? 'global'}
    />
  );
}
