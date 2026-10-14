import { getRbacCatalogState } from '@/shared/api/rbac-catalog';
import { PermissionSetsScreen } from '@/features/permission-sets';
import { NoTenantNotice } from '@/widgets/no-tenant-notice';

export const dynamic = 'force-dynamic';

/**
 * IAM 「IAM 관리 역할」 화면 (TASK-PC-FE-228 — TASK-PC-FE-225 스텁 대체;
 * «권한 세트» 에서 TASK-PC-FE-338 / ADR-MONO-083 D9 가 이름을 바꿨다 — 이
 * 화면은 `admin_roles`(IAM 관리 평면)만 보여주고, 테넌트별 도메인 업무 권한은
 * 별도 화면 「업무 권한 세트」(`/domain-permission-sets`)가 관리한다).
 *
 * `permission_set_id`는 물리적으로 `admin_roles.id`를 가리킨다(ADR-MONO-020
 * § D5) — 신규 백엔드 엔티티가 아니다. 별도 `GET /api/admin/permission-sets`
 * 뷰는 의도적으로 미구현(BE-486 결정) — 이 화면은 TASK-PC-FE-227과 **같은**
 * `getRbacCatalogState()`(`shared/api/rbac-catalog.ts`, `GET
 * /api/admin/roles` + `GET /api/admin/permissions`)를 소비하되, role 목록을
 * "관리 역할" 관점으로 재프레이밍해서 보여준다.
 *
 * Server component. Resilience는 `getRbacCatalogState()`가 처리:
 *   - 401 → `redirect('/login')`.
 *   - no active tenant → "select a tenant" 게이트.
 *   - 403 PERMISSION_DENIED (lacks `operator.manage`) → inline "not
 *     permitted".
 *   - 503 / timeout → degraded notice (콘솔 셸은 유지).
 *
 * v1 read-only. 사용 배정 수(assignment usage count)는 BE-486 응답에 없어
 * 화면에서 생략("—") — 후속 과제(프런트 N+1 집계 금지, task Failure
 * Scenario).
 */
export default async function PermissionSetsPage() {
  const state = await getRbacCatalogState();

  if (state.noTenant) {
    return (
      <section aria-labelledby="permission-sets-heading">
        <h1
          id="permission-sets-heading"
          className="mb-6 text-2xl font-semibold"
        >
          IAM 관리 역할
        </h1>
        {await NoTenantNotice({
          testId: 'permission-sets-no-tenant',
          description: (
            <>
              IAM 관리 역할 조회는 테넌트 선택 후 이용할 수 있습니다. 상단의
              테넌트 스위처에서 테넌트를 선택한 뒤 다시 시도하세요.
            </>
          ),
        })}
      </section>
    );
  }

  if (state.permissionError) {
    return (
      <section aria-labelledby="permission-sets-heading">
        <h1
          id="permission-sets-heading"
          className="mb-6 text-2xl font-semibold"
        >
          IAM 관리 역할
        </h1>
        <div
          role="status"
          data-testid="permission-sets-permission-denied"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          IAM 관리 역할 조회는 operator.manage 권한이 필요합니다 (SUPER_ADMIN
          또는 자기 테넌트 TENANT_ADMIN).
        </div>
      </section>
    );
  }

  if (state.degraded || !state.roles || !state.permissions) {
    return (
      <section aria-labelledby="permission-sets-heading">
        <h1
          id="permission-sets-heading"
          className="mb-6 text-2xl font-semibold"
        >
          IAM 관리 역할
        </h1>
        <div
          role="status"
          data-testid="permission-sets-degraded"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          IAM 관리 역할 서비스를 일시적으로 불러올 수 없습니다. 콘솔의 다른
          기능은 계속 사용할 수 있습니다. 잠시 후 다시 시도하세요.
        </div>
      </section>
    );
  }

  return (
    <PermissionSetsScreen
      permissionSets={state.roles}
      scope={state.scope ?? 'global'}
    />
  );
}
