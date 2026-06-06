import { redirect } from 'next/navigation';
import { ApiError, FinanceUnavailableError } from '@/shared/api/errors';
import { getAccount, getBalances, listTransactions } from './finance-api';
import type {
  Account,
  BalancesResponse,
  TransactionsResponse,
} from './types';

/**
 * Server-side finance operations section state for the
 * `(console)/finance` route (TASK-PC-FE-009 — the THIRD non-GAP
 * federation; closes the non-IAM federation cycle). STRICTLY READ-ONLY
 * — no mutation ever.
 *
 * Eligibility gate (console-integration-contract § 2.4.7, reusing the
 * § 2.4.5 tenant-model divergence): finance resolves the operator's
 * tenant from the JWT `tenant_id ∈ {finance,*}` claim producer-side —
 * the console does NOT send a tenant. To avoid fabricating a
 * cross-tenant call, the `(console)/finance` PAGE (the app layer — the
 * layer allowed to compose `features/*`) first resolves the operator's
 * finance eligibility from the data-driven registry (§ 2.2,
 * `getCatalog()`) and passes it in here. If not eligible the section
 * blocks with an actionable "no finance-scoped access" state and NO
 * finance call is ever made. finance still rejects cross-tenant
 * producer-side regardless (`403 TENANT_FORBIDDEN`, never weakened
 * here).
 *
 * Account-id-driven (§ 2.4.7, honest finance constraint): finance v1
 * has NO list/search GET. The section is initialised WITHOUT an
 * accountId (no fabricated list call); the operator supplies one via
 * the AccountLookup, and the page re-renders / hooks fetch when the
 * id changes. This state seed therefore carries only the eligibility
 * outcome — the actual account / balances / transactions are
 * accountId-driven (fetched via the client hooks behind the proxy
 * routes, OR a server-side fetch when accountId is provided).
 *
 * Resilience boundary (§ 2.4.7 / § 2.5, mirrors `scm-state.ts`):
 *   - `401` (IAM OIDC session expired) → `redirect('/login')` — a
 *     WHOLE-SESSION re-login, NOT a per-section degrade (no partial
 *     authed state; consistent with the FE-002..008 401 discipline).
 *   - `403` (token not finance-scoped / insufficient scope) → a
 *     non-crashing inline "not available / not scoped" state.
 *   - `404 ACCOUNT_NOT_FOUND` → an inline actionable "no such account"
 *     state for the lookup (NOT a crash, NOT a re-login).
 *   - `503` / timeout / network → DEGRADED — ONLY the finance section
 *     renders a degraded notice; the console shell + the IAM / wms /
 *     scm sections stay intact.
 *   - **no 429 handling** (§ 2.4.7): finance has no documented 429;
 *     a 429 would land as an unexpected ApiError → degrade rather
 *     than crash (no fabricated backoff).
 *   - any other producer error → degrade rather than crash.
 */
export interface FinanceSectionState {
  /** The looked-up account (when an `accountId` is provided AND the
   *  fetch succeeded). */
  account: Account | null;
  /** Balances for the looked-up account (success path). */
  balances: BalancesResponse | null;
  /** First-page transactions for the looked-up account (success
   *  path). */
  transactions: TransactionsResponse | null;
  /** True when the operator is not finance-eligible (no finance
   *  product/tenant in their registry) — actionable block, no finance
   *  call fabricated. */
  notEligible: boolean;
  /** True on a 403 (token not finance-scoped / insufficient scope) —
   *  inline. */
  forbidden: boolean;
  /** True on a 404 ACCOUNT_NOT_FOUND for the supplied `accountId` —
   *  inline actionable. */
  notFound: boolean;
  /** True on 503 / timeout / network — finance section degrades only. */
  degraded: boolean;
}

const EMPTY: FinanceSectionState = {
  account: null,
  balances: null,
  transactions: null,
  notEligible: false,
  forbidden: false,
  notFound: false,
  degraded: false,
};

/**
 * @param eligible    whether the operator is finance-eligible, resolved
 *   by the page from the data-driven registry. `false` ⇒ block (no
 *   finance call).
 * @param accountId   optional accountId — when provided, the section
 *   seeds the account + balances + first-page transactions. When
 *   absent, the section renders the AccountLookup empty state (finance
 *   v1 has no list/search GET — account-id-driven).
 */
export async function getFinanceSectionState(
  eligible: boolean,
  accountId?: string | null,
): Promise<FinanceSectionState> {
  if (!eligible) {
    // Not finance-eligible — never fabricate a cross-tenant call.
    return { ...EMPTY, notEligible: true };
  }
  if (!accountId || !accountId.trim()) {
    // No accountId supplied (the honest finance constraint — no
    // list/search). The section renders the AccountLookup empty
    // state; no finance call yet. NOT a degrade — just the
    // initial state.
    return { ...EMPTY };
  }

  try {
    const id = accountId.trim();
    const [account, balances, transactions] = await Promise.all([
      getAccount(id),
      getBalances(id),
      listTransactions(id, { page: 0, size: 20 }),
    ]);
    return {
      account,
      balances,
      transactions,
      notEligible: false,
      forbidden: false,
      notFound: false,
      degraded: false,
    };
  } catch (err) {
    if (err instanceof ApiError && err.status === 401) {
      // No partial authed state → clean WHOLE-SESSION re-login.
      redirect('/login');
    }
    if (err instanceof ApiError && err.status === 403) {
      // Token not finance-scoped → inline "not available / not
      // scoped".
      return { ...EMPTY, forbidden: true };
    }
    if (
      err instanceof ApiError &&
      err.status === 404 &&
      (err.code === 'ACCOUNT_NOT_FOUND' || err.code.startsWith('HTTP_404'))
    ) {
      // Inline "no such account" — actionable, no crash.
      return { ...EMPTY, notFound: true };
    }
    if (err instanceof FinanceUnavailableError) {
      // Degrade ONLY the finance section — shell + IAM / wms / scm
      // sections intact.
      return { ...EMPTY, degraded: true };
    }
    // Any other producer error (incl. an unexpected 429 — finance
    // has no documented rate-limit, so it falls here, not into a
    // fabricated backoff path) → degrade rather than crash.
    return { ...EMPTY, degraded: true };
  }
}
