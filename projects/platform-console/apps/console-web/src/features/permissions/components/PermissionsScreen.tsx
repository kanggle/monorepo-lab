import type { Role } from '@/shared/api/rbac-catalog';

/**
 * IAM 「권한」 화면 (TASK-PC-FE-227 — TASK-PC-FE-225 스텁 대체).
 *
 * v1 read-only — no edit affordance (the absence of any edit button naturally
 * conveys this, per the task Edge Case). Plain Server Component: the
 * role→permission drill-down uses native `<details>/<summary>` so no client
 * JS is needed for the interaction (architecture.md § Server vs Client
 * Components "Default: Server Component").
 *
 * Renders BOTH TASK-BE-486 reads:
 *   - the canonical permission-key catalog (`GET /api/admin/permissions`,
 *     includes keys not yet granted to any role);
 *   - the role catalog with each role's permission-key set (`GET
 *     /api/admin/roles`) — clicking a role (`<summary>`) expands its keys.
 *
 * `scope` is echoed verbatim from the producer (never hard-assumed) so the
 * screen never mislabels the catalog's tenant-independence (task
 * Implementation Notes / Edge Case).
 */

export interface PermissionsScreenProps {
  roles: Role[];
  permissions: string[];
  scope: string;
}

function ScopeLabel({ scope }: { scope: string }) {
  return (
    <span data-testid="permissions-scope-note">
      {scope === 'global'
        ? '전역(global) — 테넌트와 무관하게 전체 플랫폼에 적용됩니다.'
        : scope}
    </span>
  );
}

export function PermissionsScreen({
  roles,
  permissions,
  scope,
}: PermissionsScreenProps) {
  return (
    <section aria-labelledby="permissions-heading">
      <h1 id="permissions-heading" className="mb-2 text-2xl font-semibold">
        권한
      </h1>
      <p className="mb-1 max-w-3xl text-sm text-muted-foreground">
        권한 키 카탈로그와 각 역할(role)이 보유한 권한 키를 조회합니다. 읽기
        전용입니다 — 역할·권한 정의는 seed(Flyway)로만 변경됩니다.
      </p>
      <p className="mb-6 text-xs text-muted-foreground">
        스코프: <ScopeLabel scope={scope} />
      </p>

      <h2 className="mb-2 text-lg font-semibold">권한 키 카탈로그</h2>
      {permissions.length === 0 ? (
        <p
          className="mb-8 text-sm text-muted-foreground"
          data-testid="permissions-catalog-empty"
        >
          정의된 권한 키가 없습니다.
        </p>
      ) : (
        <ul
          className="mb-8 flex flex-wrap gap-1.5"
          data-testid="permissions-catalog-list"
        >
          {permissions.map((key) => (
            <li key={key}>
              <span className="rounded bg-muted px-1.5 py-0.5 font-mono text-xs text-foreground">
                {key}
              </span>
            </li>
          ))}
        </ul>
      )}

      <h2 className="mb-2 text-lg font-semibold">역할(Role) 목록</h2>
      <p className="mb-4 text-xs text-muted-foreground">
        역할을 클릭하면 그 역할이 보유한 권한 키가 펼쳐집니다.
      </p>
      {roles.length === 0 ? (
        <p
          className="text-sm text-muted-foreground"
          data-testid="permissions-roles-empty"
        >
          정의된 역할이 없습니다.
        </p>
      ) : (
        <ul className="space-y-2" data-testid="permissions-roles-list">
          {roles.map((role) => (
            <li key={role.id}>
              <details
                className="rounded-lg border border-border bg-background p-4"
                data-testid={`permissions-role-${role.name}`}
              >
                <summary className="cursor-pointer select-none text-sm font-medium text-foreground">
                  {role.name}
                  <span className="ml-2 text-xs font-normal text-muted-foreground">
                    {role.description}
                  </span>
                  <span
                    className="ml-2 text-xs font-normal text-muted-foreground"
                    data-testid={`permissions-role-${role.name}-count`}
                  >
                    ({role.permissions.length}개 권한)
                  </span>
                </summary>
                <div className="mt-3">
                  {role.permissions.length === 0 ? (
                    <p
                      className="text-xs text-muted-foreground"
                      data-testid={`permissions-role-${role.name}-empty`}
                    >
                      이 역할에는 부여된 권한이 없습니다.
                    </p>
                  ) : (
                    <ul
                      className="flex flex-wrap gap-1.5"
                      data-testid={`permissions-role-${role.name}-keys`}
                    >
                      {role.permissions.map((key) => (
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
