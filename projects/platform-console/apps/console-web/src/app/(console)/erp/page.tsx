import Link from 'next/link';
import { getCatalog } from '@/features/catalog';
import { ApiError } from '@/shared/api/errors';
import { redirect } from 'next/navigation';
import {
  ErpOpsScreen,
  getErpSectionState,
} from '@/features/erp-ops';

export const dynamic = 'force-dynamic';

/**
 * erp operations section route (TASK-PC-FE-010 — ADR-MONO-013 Phase
 * 6; the FOURTH non-IAM domain federated by the console — the FIRST
 * internal-system-primary). An in-console nav destination, NOT a
 * catalog product re-route — the catalog `erp.baseRoute` stays
 * data-driven (resolveConsoleRoute leaves non-IAM products on their
 * registry baseRoute; an `available:false` erp is handled by the
 * existing catalog "coming soon" path — this route does not
 * hard-crash when erp is unavailable).
 *
 * Server component. STRICTLY READ-ONLY. erp is reached server-side
 * with the HttpOnly **IAM OIDC access token** (NOT the IAM exchanged
 * operator token — § 2.4.8 reuses the § 2.4.5 per-domain credential
 * rule; the #569 invariant is GAP-domain-scoped and erp *requires*
 * the IAM OIDC token).
 *
 * Eligibility (§ 2.4.8 reusing § 2.4.5 tenant-model divergence):
 * erp resolves the tenant from the JWT `tenant_id ∈ {erp,*}` claim
 * producer-side — the console sends no tenant. To avoid fabricating
 * a cross-tenant call, the page resolves the operator's erp
 * eligibility from the data-driven registry (the app layer is the
 * layer allowed to compose `features/*`) and passes it into
 * `getErpSectionState()`.
 *
 * E3 first-class (§ 2.4.8): the page reads an optional `?asOf=`
 * query param. The producer client threads it through verbatim and
 * returns the state-at-that-instant (NOT current state). When
 * absent the producer resolves to "today" (UTC).
 *
 * Resilience (§ 2.5): 401 → whole-session re-login; 403 → inline
 * "not scoped"; 503 / timeout → only this section degrades (the
 * `(console)` shell + IAM / wms / scm / finance sections stay).
 * **No 429 handling** — erp has no documented 429 (identical to
 * finance).
 */
export default async function ErpPage({
  searchParams,
}: {
  searchParams?: Promise<{ asOf?: string }>;
}) {
  // Eligibility pre-flight from the data-driven registry (§ 2.2).
  let eligible = false;
  let registryDegraded = false;
  try {
    const catalog = await getCatalog();
    const erp = catalog.products.find((p) => p.productKey === 'erp');
    eligible = Boolean(erp && erp.available && erp.tenants.length > 0);
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) {
      redirect('/login');
    }
    registryDegraded = true;
  }

  if (registryDegraded) {
    return (
      <section aria-labelledby="erp-heading">
        <h1 id="erp-heading" className="mb-6 text-2xl font-semibold">
          ERP 운영
        </h1>
        <div
          role="status"
          data-testid="erp-degraded"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          erp 운영 정보를 일시적으로 불러올 수 없습니다. 콘솔의 다른
          기능은 계속 사용할 수 있습니다. 잠시 후 다시 시도하세요.
        </div>
      </section>
    );
  }

  const sp = (await searchParams) ?? {};
  const asOf = sp.asOf?.trim() || null;

  const state = await getErpSectionState(eligible, asOf);

  if (state.notEligible) {
    return (
      <section aria-labelledby="erp-heading">
        <h1 id="erp-heading" className="mb-6 text-2xl font-semibold">
          ERP 운영
        </h1>
        <div
          role="status"
          data-testid="erp-not-eligible"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          <p className="mb-2 font-medium text-foreground">
            erp 운영 화면에 대한 접근 권한이 없습니다.
          </p>
          <p>
            현재 로그인한 운영자에게 erp 테넌트 스코프가 부여되어
            있지 않습니다. 접근이 필요하면 운영자 관리자에게
            문의하세요.
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

  if (state.forbidden) {
    return (
      <section aria-labelledby="erp-heading">
        <h1 id="erp-heading" className="mb-6 text-2xl font-semibold">
          ERP 운영
        </h1>
        <div
          role="status"
          data-testid="erp-forbidden"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          erp 운영 화면을 조회할 권한이 없습니다. (테넌트 스코프 /
          역할 / 데이터 스코프 확인이 필요합니다.)
        </div>
      </section>
    );
  }

  if (state.degraded) {
    return (
      <section aria-labelledby="erp-heading">
        <h1 id="erp-heading" className="mb-6 text-2xl font-semibold">
          ERP 운영
        </h1>
        <div
          role="status"
          data-testid="erp-degraded"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          erp 운영 정보를 일시적으로 불러올 수 없습니다. 콘솔의 다른
          기능은 계속 사용할 수 있습니다. 잠시 후 다시 시도하세요.
        </div>
      </section>
    );
  }

  return (
    <ErpOpsScreen
      initialDepartments={state.departments}
      initialEmployees={state.employees}
      initialJobGrades={state.jobGrades}
      initialCostCenters={state.costCenters}
      initialBusinessPartners={state.businessPartners}
      // TASK-PC-FE-049 — read-model employee org-view (read-only).
      initialEmployeeOrgViews={state.employeeOrgViews}
      // TASK-PC-FE-051 — approval workflow (결재함) first-page snapshots.
      initialApprovalRequests={state.approvalRequests}
      initialApprovalInbox={state.approvalInbox}
      // TASK-PC-FE-055 — read-model delegation-fact (위임 현황, read-only).
      initialDelegationFacts={state.delegationFacts}
      // TASK-PC-FE-046/048 — erp masterdata write across all 5 masters.
      // Eligible operators see the write affordances; the producer's E6
      // fail-CLOSED authz is the authority (a 403 is surfaced inline — the
      // console never pre-judges write authority). § 2.4.8 *Masterdata write*.
      mastersWritable
    />
  );
}
