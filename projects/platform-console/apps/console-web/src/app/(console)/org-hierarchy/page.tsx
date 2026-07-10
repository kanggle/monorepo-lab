import Link from 'next/link';
import { getOrgHierarchyState, OrgHierarchyScreen } from '@/features/org-hierarchy';

export const dynamic = 'force-dynamic';

/**
 * ADR-047 org-node hierarchy route (TASK-PC-FE-237) — the IAM ▸ 조직 계층 menu
 * destination. The company → service → domain 3-axis surface: org-node tree
 * CRUD + entitlement-ceiling editor + subtree-scoped `ORG_ADMIN` assignment,
 * gated by `org.manage` (SUPER_ADMIN or a parent node's ORG_ADMIN).
 *
 * Server component: the initial flat node list is fetched server-side via the
 * IAM admin-service client with the HttpOnly operator token + active tenant
 * (`getOrgHierarchyState()`). Resilience is handled there (mirrors
 * `tenants/page.tsx`):
 *   - 401 → `redirect('/login')` (inside `getOrgHierarchyState`).
 *   - no active tenant → a "select a tenant" gate (a SUPER_ADMIN selects `*`).
 *   - 403 (lacks `org.manage`) → an inline "org.manage required" notice.
 *   - 503 / timeout → a degraded notice; the console shell stays intact.
 */
export default async function OrgHierarchyPage() {
  const state = await getOrgHierarchyState();

  if (state.noTenant) {
    return (
      <section aria-labelledby="org-hierarchy-heading">
        <h1 id="org-hierarchy-heading" className="mb-6 text-2xl font-semibold">
          조직 계층
        </h1>
        <div
          role="status"
          data-testid="org-hierarchy-no-tenant"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          <p className="mb-2 font-medium text-foreground">
            테넌트를 먼저 선택하세요.
          </p>
          <p>
            조직 계층 관리는 활성 테넌트가 선택되어 있어야 합니다. 상단의 테넌트
            스위처에서 플랫폼 스코프(*)를 선택한 뒤 다시 시도하세요.
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
      <section aria-labelledby="org-hierarchy-heading">
        <h1 id="org-hierarchy-heading" className="mb-6 text-2xl font-semibold">
          조직 계층
        </h1>
        <div
          role="status"
          data-testid="org-hierarchy-permission-denied"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          조직 계층 관리는 org.manage 권한이 필요합니다 (SUPER_ADMIN 또는 상위
          노드의 ORG_ADMIN). 이 화면을 조회·변경할 권한이 없습니다.
        </div>
      </section>
    );
  }

  if (state.degraded) {
    return (
      <section aria-labelledby="org-hierarchy-heading">
        <h1 id="org-hierarchy-heading" className="mb-6 text-2xl font-semibold">
          조직 계층
        </h1>
        <div
          role="status"
          data-testid="org-hierarchy-degraded"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          조직 계층 서비스를 일시적으로 불러올 수 없습니다. 콘솔의 다른 기능은
          계속 사용할 수 있습니다. 잠시 후 다시 시도하세요.
        </div>
      </section>
    );
  }

  return <OrgHierarchyScreen initial={state.nodes} />;
}
