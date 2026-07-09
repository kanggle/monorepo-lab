import type { Role } from '@/shared/api/rbac-catalog';

/**
 * IAM 「권한 세트」 화면 (TASK-PC-FE-228 — TASK-PC-FE-225 스텁 대체).
 *
 * `permission_set_id`(`operator_tenant_assignment`의 per-assignment role-set
 * narrowing 필드, ADR-MONO-020 § D5)는 물리적으로 `admin_roles.id`를 가리킨다
 * — 신규 테이블/엔티티가 아니다. `GET /api/admin/permission-sets` 뷰는 의도적으
 * 로 미구현(BE-486 결정) — `GET /api/admin/roles`가 이미 각 role을 permission
 * 키 집합과 함께 반환하므로 이 화면은 그 **같은 응답을 "권한 세트" 관점으로 재
 * 프레이밍**해서 보여준다(role = 권한의 집합 = 권한 세트).
 *
 * v1 read-only — no edit affordance. Plain Server Component: native
 * `<details>/<summary>` for the drill-down, no client JS needed.
 *
 * 사용 배정 수(assignment usage count)는 BE-486 응답에 없다 — AC대로 표시를
 * 생략("—")하고 후속 과제로 명시한다(프런트 N+1 집계는 하지 않는다, task
 * Failure Scenario).
 */

export interface PermissionSetsScreenProps {
  /** Reframed name for the SAME `admin_roles` rows `features/permissions`
   *  renders as "roles" — one shared producer response, two view-models. */
  permissionSets: Role[];
  scope: string;
}

function ScopeLabel({ scope }: { scope: string }) {
  return (
    <span data-testid="permission-sets-scope-note">
      {scope === 'global'
        ? '전역(global) — 테넌트와 무관하게 전체 플랫폼에 적용됩니다.'
        : scope}
    </span>
  );
}

export function PermissionSetsScreen({
  permissionSets,
  scope,
}: PermissionSetsScreenProps) {
  return (
    <section aria-labelledby="permission-sets-heading">
      <h1
        id="permission-sets-heading"
        className="mb-2 text-2xl font-semibold"
      >
        권한 세트
      </h1>
      <p className="mb-1 max-w-3xl text-sm text-muted-foreground">
        배정(<code className="font-mono text-xs">operator_tenant_assignment</code>)의{' '}
        <code className="font-mono text-xs">permissionSetId</code>가 가리키는
        권한 세트 목록입니다. 물리적으로 <code className="font-mono text-xs">admin_roles</code>를
        재사용합니다(별도 테이블 아님) — 아래 각 세트는 「권한」 화면의
        역할(role)과 동일한 데이터를 &ldquo;권한 세트&rdquo; 관점으로
        보여줍니다. 읽기 전용입니다.
      </p>
      <p className="mb-6 text-xs text-muted-foreground">
        스코프: <ScopeLabel scope={scope} />
      </p>

      <div
        role="note"
        data-testid="permission-sets-null-notice"
        className="mb-8 rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
      >
        <p className="mb-1 font-medium text-foreground">
          권한 세트 미지정 (permission_set_id = NULL)
        </p>
        <p>
          배정에 권한 세트가 지정되지 않은 경우, 그 배정은 별도로 좁혀지지
          않고{' '}
          <strong className="text-foreground">
            운영자 본인의 role을 그대로 상속
          </strong>
          합니다(operator-level 상속). 「운영자 관리」 배정 화면의 동일 필드
          (
          <code className="font-mono text-xs">permissionSetId</code> 생략)와
          같은 의미입니다.
        </p>
      </div>

      <h2 className="mb-2 text-lg font-semibold">권한 세트 목록</h2>
      <p className="mb-4 text-xs text-muted-foreground">
        세트를 클릭하면 그 세트가 보유한 권한 키가 펼쳐집니다. 사용 배정 수
        (usage count)는 현재 API 응답에 포함되지 않아 표시하지
        않습니다(&ldquo;—&rdquo;) — 후속 과제(집계 API 필요, 프런트에서
        N+1으로 집계하지 않습니다).
      </p>
      {permissionSets.length === 0 ? (
        <p
          className="text-sm text-muted-foreground"
          data-testid="permission-sets-empty"
        >
          정의된 권한 세트가 없습니다.
        </p>
      ) : (
        <ul className="space-y-2" data-testid="permission-sets-list">
          {permissionSets.map((set) => (
            <li key={set.id}>
              <details
                className="rounded-lg border border-border bg-background p-4"
                data-testid={`permission-set-${set.name}`}
              >
                <summary className="cursor-pointer select-none text-sm font-medium text-foreground">
                  {set.name}
                  <span className="ml-2 text-xs font-normal text-muted-foreground">
                    (id={set.id}) {set.description}
                  </span>
                  <span className="ml-2 text-xs font-normal text-muted-foreground">
                    사용 배정 수:{' '}
                    <span
                      data-testid={`permission-set-${set.name}-usage`}
                    >
                      —
                    </span>
                  </span>
                </summary>
                <div className="mt-3">
                  {set.permissions.length === 0 ? (
                    <p
                      className="text-xs text-muted-foreground"
                      data-testid={`permission-set-${set.name}-empty`}
                    >
                      이 세트에는 권한이 없습니다.
                    </p>
                  ) : (
                    <ul
                      className="flex flex-wrap gap-1.5"
                      data-testid={`permission-set-${set.name}-keys`}
                    >
                      {set.permissions.map((key) => (
                        <li key={key}>
                          <span className="rounded bg-muted px-1.5 py-0.5 font-mono text-xs text-foreground">
                            {key}
                          </span>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              </details>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
