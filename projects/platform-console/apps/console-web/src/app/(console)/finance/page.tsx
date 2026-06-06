import Link from 'next/link';
import { getCatalog } from '@/features/catalog';
import { ApiError } from '@/shared/api/errors';
import { redirect } from 'next/navigation';
import {
  FinanceOpsScreen,
  getFinanceSectionState,
} from '@/features/finance-ops';

export const dynamic = 'force-dynamic';

/**
 * finance operations section route (TASK-PC-FE-009 — ADR-MONO-013 Phase
 * 5; the THIRD non-IAM domain federated by the console — closes the
 * non-IAM federation cycle). An in-console nav destination, NOT a
 * catalog product re-route — the catalog `finance.baseRoute` stays
 * data-driven (resolveConsoleRoute leaves non-IAM products on their
 * registry baseRoute; an `available:false` finance is handled by the
 * existing catalog "coming soon" path — this route does not
 * hard-crash when finance is unavailable).
 *
 * Server component. STRICTLY READ-ONLY. finance is reached server-side
 * with the HttpOnly **IAM OIDC access token** (NOT the IAM exchanged
 * operator token — § 2.4.7 reuses the § 2.4.5 per-domain credential
 * rule; the #569 invariant is GAP-domain-scoped and finance
 * *requires* the IAM OIDC token).
 *
 * Eligibility (§ 2.4.7 reusing § 2.4.5 tenant-model divergence):
 * finance resolves the tenant from the JWT `tenant_id ∈ {finance,*}`
 * claim producer-side — the console sends no tenant. To avoid
 * fabricating a cross-tenant call, the page resolves the operator's
 * finance eligibility from the data-driven registry (the app layer
 * is the layer allowed to compose `features/*`) and passes it into
 * `getFinanceSectionState()`.
 *
 * Account-id-driven (§ 2.4.7, honest finance constraint): finance v1
 * has NO list/search GET. The page reads an optional `?accountId=`
 * query param; when absent the lookup form is shown empty. When
 * present, the section is seeded server-side with account + balances
 * + first-page transactions.
 *
 * Resilience (§ 2.5): 401 → whole-session re-login; 403 → inline "not
 * scoped"; 404 `ACCOUNT_NOT_FOUND` → inline actionable; 503 / timeout
 * → only this section degrades (the `(console)` shell + IAM / wms /
 * scm sections stay). **No 429 handling** — finance has no documented
 * 429.
 */
export default async function FinancePage({
  searchParams,
}: {
  searchParams?: Promise<{ accountId?: string }>;
}) {
  // Eligibility pre-flight from the data-driven registry (§ 2.2). A
  // registry 401 → whole-session re-login (no partial authed state); a
  // registry failure → treat as degraded (cannot prove ineligibility
  // from a failed registry — never block on an unproven negative).
  let eligible = false;
  let registryDegraded = false;
  try {
    const catalog = await getCatalog();
    const fin = catalog.products.find((p) => p.productKey === 'finance');
    eligible = Boolean(fin && fin.available && fin.tenants.length > 0);
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) {
      redirect('/login');
    }
    registryDegraded = true;
  }

  if (registryDegraded) {
    return (
      <section aria-labelledby="finance-heading">
        <h1 id="finance-heading" className="mb-6 text-2xl font-semibold">
          Finance 운영
        </h1>
        <div
          role="status"
          data-testid="finance-degraded"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          finance 운영 정보를 일시적으로 불러올 수 없습니다. 콘솔의 다른
          기능은 계속 사용할 수 있습니다. 잠시 후 다시 시도하세요.
        </div>
      </section>
    );
  }

  const sp = (await searchParams) ?? {};
  const accountId = sp.accountId?.trim() || null;

  const state = await getFinanceSectionState(eligible, accountId);

  if (state.notEligible) {
    return (
      <section aria-labelledby="finance-heading">
        <h1 id="finance-heading" className="mb-6 text-2xl font-semibold">
          Finance 운영
        </h1>
        <div
          role="status"
          data-testid="finance-not-eligible"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          <p className="mb-2 font-medium text-foreground">
            finance 운영 화면에 대한 접근 권한이 없습니다.
          </p>
          <p>
            현재 로그인한 운영자에게 finance 테넌트 스코프가 부여되어
            있지 않습니다. 접근이 필요하면 운영자 관리자에게 문의하세요.
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
      <section aria-labelledby="finance-heading">
        <h1 id="finance-heading" className="mb-6 text-2xl font-semibold">
          Finance 운영
        </h1>
        <div
          role="status"
          data-testid="finance-forbidden"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          finance 운영 화면을 조회할 권한이 없습니다. (테넌트 스코프 /
          역할 확인이 필요합니다.)
        </div>
      </section>
    );
  }

  if (state.degraded) {
    return (
      <section aria-labelledby="finance-heading">
        <h1 id="finance-heading" className="mb-6 text-2xl font-semibold">
          Finance 운영
        </h1>
        <div
          role="status"
          data-testid="finance-degraded"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          finance 운영 정보를 일시적으로 불러올 수 없습니다. 콘솔의 다른
          기능은 계속 사용할 수 있습니다. 잠시 후 다시 시도하세요.
        </div>
      </section>
    );
  }

  // notFound is rendered by the FinanceOpsScreen inline (so the
  // lookup form stays mounted), not as a whole-section block.
  return (
    <FinanceOpsScreen
      initialAccountId={accountId}
      initialAccount={state.account}
      initialBalances={state.balances}
      initialTransactions={state.transactions}
    />
  );
}
