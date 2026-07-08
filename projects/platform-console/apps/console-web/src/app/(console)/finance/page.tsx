import Link from 'next/link';
import { getCatalog } from '@/features/catalog';
import { ApiError } from '@/shared/api/errors';
import { redirect } from 'next/navigation';
import {
  FinanceOverviewScreen,
  getFinanceOverviewState,
} from '@/features/finance-overview';

export const dynamic = 'force-dynamic';

/**
 * Finance domain overview landing — `/finance` (TASK-PC-FE-229). Repoints
 * the domain root at a **live overview snapshot** (orthodox parity with
 * every other domain's 개요-at-root convention — `/wms`, `/scm`, `/erp`,
 * `/ecommerce`, `/iam` are all 개요 landings). The former account surface
 * that used to live at `/finance` is **relocated** to `/finance/accounts`
 * (a separate, unchanged route + feature — see
 * `app/(console)/finance/accounts/page.tsx`).
 *
 * Supersedes the PARKED TASK-PC-FE-160 "finance landing overview" — see
 * `features/finance-overview/api/overview-state.ts` for the honesty
 * argument (ledger browsable reads + a SINGLE default-account snapshot,
 * never a fabricated cross-account count/aggregate).
 *
 * Server component. STRICTLY READ-ONLY. Mirrors `app/(console)/ledger/
 * page.tsx`'s eligibility waterfall verbatim (registry `productKey=
 * 'finance'` pre-flight → registryDegraded → notEligible → forbidden →
 * happy; a per-tile `ledgerDegraded`/`accountDegraded` — never a
 * whole-page "degraded" branch — see § overview-state.ts independent
 * degrade rule).
 *
 * Eligibility (§ 2.4.7 reusing § 2.4.5 tenant-model divergence): finance
 * resolves the tenant from the JWT `tenant_id ∈ {finance,*}` claim
 * producer-side — the console sends no tenant. To avoid fabricating a
 * cross-tenant call, the page resolves the operator's finance eligibility
 * from the data-driven registry (the app layer is the layer allowed to
 * compose `features/*`) and passes it into `getFinanceOverviewState()`.
 *
 * Resilience (§ 2.5): 401 → whole-session re-login; 403 → inline "not
 * scoped" (shared-credential rule, § overview-state.ts); 503 / timeout in
 * EITHER the registry OR a leg → that section degrades only (the
 * `(console)` shell + the other Finance tiles + IAM / wms / scm sections
 * stay).
 */
export default async function FinanceOverviewPage({
  searchParams,
}: {
  searchParams?: Promise<{ accountId?: string }>;
}) {
  // Legacy bookmark honesty (Edge Cases): a pre-TASK-PC-FE-229 bookmark of
  // `/finance?accountId=X` (the former account-lookup destination) now
  // lands on the overview — the query is otherwise IGNORED (never
  // fabricates an account fetch here), but when present we surface a
  // direct link to its new home `/finance/accounts?accountId=X` so the
  // bookmark isn't a hard break.
  const sp = (await searchParams) ?? {};
  const legacyAccountId = sp.accountId?.trim() || null;

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
      <section aria-labelledby="finance-overview-heading">
        <h1
          id="finance-overview-heading"
          className="mb-6 text-2xl font-semibold"
        >
          Finance 개요
        </h1>
        <div
          role="status"
          data-testid="finance-overview-registry-degraded"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          finance 개요 정보를 일시적으로 불러올 수 없습니다. 콘솔의 다른
          기능은 계속 사용할 수 있습니다. 잠시 후 다시 시도하세요.
        </div>
      </section>
    );
  }

  const state = await getFinanceOverviewState(eligible);

  if (state.notEligible) {
    return (
      <section aria-labelledby="finance-overview-heading">
        <h1
          id="finance-overview-heading"
          className="mb-6 text-2xl font-semibold"
        >
          Finance 개요
        </h1>
        <div
          role="status"
          data-testid="finance-overview-not-eligible"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          <p className="mb-2 font-medium text-foreground">
            finance 개요 화면에 대한 접근 권한이 없습니다.
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
      <section aria-labelledby="finance-overview-heading">
        <h1
          id="finance-overview-heading"
          className="mb-6 text-2xl font-semibold"
        >
          Finance 개요
        </h1>
        <div
          role="status"
          data-testid="finance-overview-forbidden"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          finance 개요를 조회할 권한이 없습니다. (테넌트 스코프 / 역할
          확인이 필요합니다.)
        </div>
      </section>
    );
  }

  // ledgerDegraded / defaultAccountMissing / accountDegraded /
  // accountNotFound are rendered by FinanceOverviewScreen inline (per-tile
  // — so the sibling section stays mounted), never a whole-page block.
  return (
    <FinanceOverviewScreen state={state} legacyAccountId={legacyAccountId} />
  );
}
