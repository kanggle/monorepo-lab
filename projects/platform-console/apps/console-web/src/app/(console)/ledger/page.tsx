import Link from 'next/link';
import { getCatalog } from '@/features/catalog';
import { ApiError } from '@/shared/api/errors';
import { redirect } from 'next/navigation';
import {
  LedgerOpsScreen,
  getLedgerSectionState,
} from '@/features/ledger-ops';

export const dynamic = 'force-dynamic';

/**
 * finance ledger operations section route (TASK-PC-FE-072 — § 2.4.7.1;
 * the SECOND finance-product service section, alongside the FE-009
 * account surface). An in-console nav destination, NOT a catalog product
 * re-route — the ledger is part of the finance product (there is no
 * separate `ledger` catalog tile).
 *
 * Server component. STRICTLY READ-ONLY. The ledger is reached server-side
 * with the HttpOnly **domain-facing IAM OIDC access token** (NOT the IAM
 * exchanged operator token — § 2.4.7.1 reuses the § 2.4.7 / § 2.4.5
 * per-domain credential rule; the #569 invariant is GAP-domain-scoped and
 * the ledger *requires* the IAM OIDC token).
 *
 * Eligibility (§ 2.4.7.1 reusing the § 2.4.7 / § 2.4.5 tenant-model
 * divergence): the ledger resolves the tenant from the JWT
 * `tenant_id ∈ {finance,*}` claim producer-side — the console sends no
 * tenant. To avoid fabricating a cross-tenant call, the page resolves the
 * operator's **finance** eligibility from the data-driven registry (the
 * ledger is part of the finance product) and passes it into
 * `getLedgerSectionState()`.
 *
 * Browsable index + id-driven entry (§ 2.4.7.1): the section seeds the
 * browsable index reads (trial balance / periods / OPEN discrepancy queue)
 * on load. The page reads an optional `?entryId=` query param; when present
 * the journal entry is additionally seeded server-side (the ledger has NO
 * entry list/search GET — entry-id-driven).
 *
 * Resilience (§ 2.5): 401 → whole-session re-login; 403 → inline "not
 * scoped"; 404 `JOURNAL_ENTRY_NOT_FOUND` (seeded entryId) → inline
 * actionable; 503 / timeout → only this section degrades (the `(console)`
 * shell + IAM / wms / scm / finance-account / erp sections stay). **No 429
 * handling** — the ledger has no documented 429.
 */
export default async function LedgerPage({
  searchParams,
}: {
  searchParams?: Promise<{ entryId?: string }>;
}) {
  // Eligibility pre-flight from the data-driven registry (§ 2.2). A
  // registry 401 → whole-session re-login (no partial authed state); a
  // registry failure → treat as degraded (cannot prove ineligibility from
  // a failed registry — never block on an unproven negative).
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
      <section aria-labelledby="ledger-heading">
        <h1 id="ledger-heading" className="mb-6 text-2xl font-semibold">
          Finance Ledger 운영
        </h1>
        <div
          role="status"
          data-testid="ledger-degraded"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          finance ledger 운영 정보를 일시적으로 불러올 수 없습니다. 콘솔의
          다른 기능은 계속 사용할 수 있습니다. 잠시 후 다시 시도하세요.
        </div>
      </section>
    );
  }

  const sp = (await searchParams) ?? {};
  const entryId = sp.entryId?.trim() || null;

  const state = await getLedgerSectionState(eligible, entryId);

  if (state.notEligible) {
    return (
      <section aria-labelledby="ledger-heading">
        <h1 id="ledger-heading" className="mb-6 text-2xl font-semibold">
          Finance Ledger 운영
        </h1>
        <div
          role="status"
          data-testid="ledger-not-eligible"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          <p className="mb-2 font-medium text-foreground">
            finance ledger 운영 화면에 대한 접근 권한이 없습니다.
          </p>
          <p>
            현재 로그인한 운영자에게 finance 테넌트 스코프가 부여되어 있지
            않습니다. 접근이 필요하면 운영자 관리자에게 문의하세요.
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
      <section aria-labelledby="ledger-heading">
        <h1 id="ledger-heading" className="mb-6 text-2xl font-semibold">
          Finance Ledger 운영
        </h1>
        <div
          role="status"
          data-testid="ledger-forbidden"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          finance ledger 운영 화면을 조회할 권한이 없습니다. (테넌트 스코프 /
          역할 확인이 필요합니다.)
        </div>
      </section>
    );
  }

  if (state.degraded) {
    return (
      <section aria-labelledby="ledger-heading">
        <h1 id="ledger-heading" className="mb-6 text-2xl font-semibold">
          Finance Ledger 운영
        </h1>
        <div
          role="status"
          data-testid="ledger-degraded"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          finance ledger 운영 정보를 일시적으로 불러올 수 없습니다. 콘솔의
          다른 기능은 계속 사용할 수 있습니다. 잠시 후 다시 시도하세요.
        </div>
      </section>
    );
  }

  // notFound (the seeded entryId 404'd) is rendered by the LedgerOpsScreen
  // inline inside the Journal Entry tab (so the lookup form stays mounted),
  // not as a whole-section block.
  return (
    <LedgerOpsScreen
      initialEntryId={entryId}
      trialBalance={state.trialBalance}
      periods={state.periods}
      discrepancies={state.discrepancies}
      initialEntry={state.entry}
      initialNotFound={state.notFound}
    />
  );
}
