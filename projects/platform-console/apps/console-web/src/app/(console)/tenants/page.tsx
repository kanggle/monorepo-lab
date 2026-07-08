import Link from 'next/link';
import { getTenantsListState, TenantsScreen } from '@/features/tenants';

export const dynamic = 'force-dynamic';

/**
 * IAM tenant-management route (TASK-PC-FE-226 — replaces the TASK-PC-FE-225
 * stub). The `/tenants` nav destination for the IAM ▸ 테넌트 menu item —
 * the isolation-boundary CRUD screen (SUPER_ADMIN only for every one of the
 * 4 producer endpoints, list read included).
 *
 * Functionally UNRELATED to `TenantSwitcher` (the operator's own
 * active-tenant session selector, `features/tenant`) — this is the tenant
 * RESOURCE management surface.
 *
 * Server component: the initial tenant page is fetched server-side via the
 * IAM admin-service client with the HttpOnly operator token + active tenant
 * (`getTenantsListState()`). Resilience is handled there:
 *   - 401 → `redirect('/login')` (handled inside `getTenantsListState`).
 *   - no active tenant → a "select a tenant" gate (a SUPER_ADMIN selects `*`
 *     via the tenant switcher; never an empty `X-Tenant-Id`).
 *   - 403 PERMISSION_DENIED / TENANT_SCOPE_DENIED (not SUPER_ADMIN) → an
 *     inline "not permitted" notice (no crash, no re-login loop) — since the
 *     LIST read itself requires SUPER_ADMIN, this single gate covers BOTH
 *     view and mutate permission (task AC: a non-SUPER_ADMIN never sees a
 *     create/edit control that would then 403).
 *   - 503 / timeout → a degraded notice; the console shell stays intact.
 */
export default async function TenantsPage() {
  const state = await getTenantsListState({ page: 0, size: 20 });

  if (state.noTenant) {
    return (
      <section aria-labelledby="tenants-heading">
        <h1 id="tenants-heading" className="mb-6 text-2xl font-semibold">
          테넌트 관리
        </h1>
        <div
          role="status"
          data-testid="tenants-no-tenant"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          <p className="mb-2 font-medium text-foreground">
            테넌트를 먼저 선택하세요.
          </p>
          <p>
            테넌트 관리는 SUPER_ADMIN 전용이며 활성 테넌트가 선택되어 있어야
            합니다. 상단의 테넌트 스위처에서 플랫폼 스코프(*)를 선택한 뒤 다시
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

  if (state.permissionError) {
    return (
      <section aria-labelledby="tenants-heading">
        <h1 id="tenants-heading" className="mb-6 text-2xl font-semibold">
          테넌트 관리
        </h1>
        <div
          role="status"
          data-testid="tenants-permission-denied"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          테넌트 관리는 SUPER_ADMIN 전용입니다. 이 화면을 조회·변경할 권한이
          없습니다.
        </div>
      </section>
    );
  }

  if (state.degraded || !state.page) {
    return (
      <section aria-labelledby="tenants-heading">
        <h1 id="tenants-heading" className="mb-6 text-2xl font-semibold">
          테넌트 관리
        </h1>
        <div
          role="status"
          data-testid="tenants-degraded"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          테넌트 관리 서비스를 일시적으로 불러올 수 없습니다. 콘솔의 다른
          기능은 계속 사용할 수 있습니다. 잠시 후 다시 시도하세요.
        </div>
      </section>
    );
  }

  return <TenantsScreen initial={state.page} />;
}
