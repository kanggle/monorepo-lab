import { getActiveTenant } from '@/shared/lib/session';
import { getCatalog } from '@/features/catalog';
import {
  getDomainPermissionSetsState,
  DomainPermissionSetsScreen,
} from '@/features/domain-permission-sets';
import { NoTenantNotice } from '@/widgets/no-tenant-notice';

export const dynamic = 'force-dynamic';

/**
 * ADR-MONO-083 D9 업무 권한 세트 route (TASK-PC-FE-338) — the IAM ▸ 업무 권한
 * 세트 menu destination. Tenant-scoped DOMAIN BUSINESS permission bundles
 * operators/groups receive — a SEPARATE entity/producer from 「IAM 관리
 * 역할」 (`/permission-sets`), gated by `domain_access.manage` (SUPER_ADMIN,
 * self-tenant TENANT_ADMIN).
 *
 * Server component: the initial catalog + sets are fetched server-side via
 * the IAM admin-service client with the HttpOnly operator token + active
 * tenant (`getDomainPermissionSetsState()`). Resilience is handled there
 * (mirrors `operator-groups/page.tsx`):
 *   - 401 → `redirect('/login')` (inside `getDomainPermissionSetsState`).
 *   - no active tenant → a "select a tenant" gate (a SUPER_ADMIN selects `*`).
 *   - 403 (lacks `domain_access.manage`) → an inline "permission required" notice.
 *   - 503 / timeout → a degraded notice; the console shell stays intact.
 *
 * `subscribedDomains` (the create/edit form's "구독 없음" disable source) is
 * resolved HERE — the `app/` layer, which may import any feature — the SAME
 * way `(console)/layout.tsx` resolves it for the sidebar badge
 * (`features/catalog` `getCatalog()` ∩ active tenant), then threaded into
 * `getDomainPermissionSetsState()`. A registry failure (or no active tenant)
 * leaves it `null` — "don't know ⇒ don't disable"; the producer's
 * `422 DOMAIN_NOT_SUBSCRIBED` stays the final authority either way.
 */
export default async function DomainPermissionSetsPage() {
  const activeTenant = await getActiveTenant();

  let subscribedDomains: string[] | null = null;
  if (activeTenant) {
    try {
      const catalog = await getCatalog();
      if (!catalog.degraded) {
        subscribedDomains = catalog.products
          .filter((p) => p.tenants.includes(activeTenant))
          .map((p) => p.productKey)
          .filter((k) => k !== 'iam');
      }
    } catch {
      subscribedDomains = null;
    }
  }

  const state = await getDomainPermissionSetsState(subscribedDomains);

  if (state.noTenant) {
    return (
      <section aria-labelledby="domain-permission-sets-heading">
        <h1 id="domain-permission-sets-heading" className="mb-6 text-2xl font-semibold">
          업무 권한 세트
        </h1>
        {await NoTenantNotice({
          testId: 'domain-permission-sets-no-tenant',
          description: (
            <>
              업무 권한 세트 관리는 활성 테넌트가 선택되어 있어야 합니다. 상단의
              테넌트 스위처에서 테넌트(SUPER_ADMIN 은 플랫폼 스코프 *)를 선택한
              뒤 다시 시도하세요.
            </>
          ),
        })}
      </section>
    );
  }

  if (state.permissionError) {
    return (
      <section aria-labelledby="domain-permission-sets-heading">
        <h1 id="domain-permission-sets-heading" className="mb-6 text-2xl font-semibold">
          업무 권한 세트
        </h1>
        <div
          role="status"
          data-testid="domain-permission-sets-permission-denied"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          {state.permissionError.code === 'TENANT_SCOPE_DENIED'
            ? '선택한 테넌트에 대한 업무 권한 세트 관리 권한이 없습니다.'
            : '업무 권한 세트 관리는 domain_access.manage 권한이 필요합니다 (SUPER_ADMIN 또는 자기 테넌트 TENANT_ADMIN). 이 화면을 조회·변경할 권한이 없습니다.'}
        </div>
      </section>
    );
  }

  if (state.degraded || !state.roles || !state.sets) {
    return (
      <section aria-labelledby="domain-permission-sets-heading">
        <h1 id="domain-permission-sets-heading" className="mb-6 text-2xl font-semibold">
          업무 권한 세트
        </h1>
        <div
          role="status"
          data-testid="domain-permission-sets-degraded"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          업무 권한 세트 서비스를 일시적으로 불러올 수 없습니다. 콘솔의 다른
          기능은 계속 사용할 수 있습니다. 잠시 후 다시 시도하세요.
        </div>
      </section>
    );
  }

  return (
    <DomainPermissionSetsScreen
      roles={state.roles}
      initialSets={state.sets}
      subscribedDomains={state.subscribedDomains}
    />
  );
}
