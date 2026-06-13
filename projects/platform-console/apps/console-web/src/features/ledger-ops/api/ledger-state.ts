import { redirect } from 'next/navigation';
import { ApiError, LedgerUnavailableError } from '@/shared/api/errors';
import {
  getTrialBalance,
  listPeriods,
  listDiscrepancies,
  getJournalEntry,
  getAccountBalance,
  getAccountEntries,
  getStatement,
} from './ledger-api';
import type {
  TrialBalance,
  PeriodsResponse,
  DiscrepanciesResponse,
  JournalEntry,
  AccountBalance,
  AccountEntriesResponse,
  Statement,
} from './types';

/**
 * Server-side finance `ledger-service` operations section state for the
 * `(console)/ledger` route (TASK-PC-FE-072 — § 2.4.7.1; the SECOND
 * finance-product service section, alongside the FE-009 account surface).
 * STRICTLY READ-ONLY — no mutation ever.
 *
 * Eligibility gate (console-integration-contract § 2.4.7.1, reusing the
 * § 2.4.7 / § 2.4.5 tenant-model divergence): the ledger resolves the
 * operator's tenant from the JWT `tenant_id ∈ {finance,*}` claim
 * producer-side — the console does NOT send a tenant. To avoid fabricating
 * a cross-tenant call, the `(console)/ledger` PAGE (the app layer — the
 * layer allowed to compose `features/*`) first resolves the operator's
 * finance eligibility from the data-driven registry (§ 2.2, `getCatalog()`
 * on `productKey==='finance'` — the ledger is part of the finance product)
 * and passes it in here. If not eligible the section blocks with an
 * actionable "no finance-scoped access" state and NO ledger call is ever
 * made. The ledger still rejects cross-tenant producer-side regardless
 * (`403 TENANT_FORBIDDEN`, never weakened here).
 *
 * Browsable index reads + id-driven entry (§ 2.4.7.1): unlike the
 * account-service (entirely account-id-driven), the ledger DOES expose
 * browsable index reads — the trial balance, the paginated periods list,
 * and the OPEN reconciliation discrepancy queue — so the section seeds
 * those on load. A journal entry, by contrast, is id-driven (the ledger
 * has NO list/search GET over entries — the honest constraint); it is
 * seeded ONLY when an `?entryId=` is supplied.
 *
 * Resilience boundary (§ 2.4.7.1 / § 2.5, mirrors `finance-state.ts` /
 * `scm-state.ts`):
 *   - `401` (IAM OIDC session expired) → `redirect('/login')` — a
 *     WHOLE-SESSION re-login, NOT a per-section degrade (no partial authed
 *     state; consistent with the FE-002..009 401 discipline).
 *   - `403` (token not finance-scoped / insufficient scope) → a
 *     non-crashing inline "not available / not scoped" state.
 *   - `404` (`JOURNAL_ENTRY_NOT_FOUND` for the supplied entryId — the
 *     only id-driven read that can 404 on seed) → an inline actionable
 *     "no such entry" state (NOT a crash, NOT a re-login).
 *   - `503` / timeout / network → DEGRADED — ONLY the ledger section
 *     renders a degraded notice; the console shell + the IAM / wms / scm /
 *     finance-account / erp sections stay intact.
 *   - **no 429 handling** (§ 2.4.7.1): the ledger has no documented 429;
 *     a 429 would land as an unexpected ApiError → degrade rather than
 *     crash (no fabricated backoff).
 *   - any other producer error → degrade rather than crash.
 */
export interface LedgerSectionState {
  /** Trial balance (browsable index read — seeded on the eligible path). */
  trialBalance: TrialBalance | null;
  /** First page of accounting periods (browsable index — seeded). */
  periods: PeriodsResponse | null;
  /** First page of OPEN reconciliation discrepancies (browsable queue —
   *  seeded with `status=OPEN`). */
  discrepancies: DiscrepanciesResponse | null;
  /** The looked-up journal entry — seeded ONLY when an `entryId` is
   *  supplied AND the fetch succeeded (id-driven; the ledger has no
   *  entry list/search GET). */
  entry: JournalEntry | null;
  /** The looked-up account balance — seeded ONLY when an `accountCode` is
   *  supplied AND the fetch succeeded (TASK-PC-FE-074). */
  accountBalance: AccountBalance | null;
  /** The first page of entries for the looked-up account — seeded ONLY
   *  when an `accountCode` is supplied AND the fetch succeeded
   *  (TASK-PC-FE-074). */
  accountEntries: AccountEntriesResponse | null;
  /** True on a 404 LEDGER_ACCOUNT_NOT_FOUND for the supplied `accountCode`
   *  — inline actionable (TASK-PC-FE-074, mirrors `notFound` for entries). */
  accountNotFound: boolean;
  /** The reconciliation statement-detail — seeded ONLY when a `statementId`
   *  is supplied AND the fetch succeeded (TASK-PC-FE-075). */
  statement: Statement | null;
  /** True on a 404 RECONCILIATION_STATEMENT_NOT_FOUND for the supplied
   *  `statementId` — inline actionable (TASK-PC-FE-075, mirrors
   *  `accountNotFound` / `notFound` for other id-driven reads). */
  statementNotFound: boolean;
  /** True when the operator is not finance-eligible (no finance
   *  product/tenant in their registry) — actionable block, no ledger call
   *  fabricated. */
  notEligible: boolean;
  /** True on a 403 (token not finance-scoped / insufficient scope) —
   *  inline. */
  forbidden: boolean;
  /** True on a 404 JOURNAL_ENTRY_NOT_FOUND for the supplied `entryId` —
   *  inline actionable. */
  notFound: boolean;
  /** True on 503 / timeout / network — ledger section degrades only. */
  degraded: boolean;
}

const EMPTY: LedgerSectionState = {
  trialBalance: null,
  periods: null,
  discrepancies: null,
  entry: null,
  accountBalance: null,
  accountEntries: null,
  accountNotFound: false,
  statement: null,
  statementNotFound: false,
  notEligible: false,
  forbidden: false,
  notFound: false,
  degraded: false,
};

/**
 * @param eligible      whether the operator is finance-eligible (the ledger
 *   is part of the finance product), resolved by the page from the
 *   data-driven registry. `false` ⇒ block (no ledger call).
 * @param entryId       optional journal entryId — when provided, the section
 *   additionally seeds that entry (id-driven). When absent, only the
 *   browsable index reads are seeded; the JournalEntryLookup renders empty.
 * @param accountCode   optional ledger account code — when provided, the
 *   section additionally seeds the account balance + first page of entries
 *   (TASK-PC-FE-074). A `404 LEDGER_ACCOUNT_NOT_FOUND` on the seeded code
 *   returns `{ ...EMPTY, accountNotFound: true }` (mirrors `notFound` for
 *   entries). When absent, `accountBalance`/`accountEntries` are `null`.
 * @param statementId   optional reconciliation statement id — when provided,
 *   the section additionally seeds the statement detail (TASK-PC-FE-075).
 *   A `404 RECONCILIATION_STATEMENT_NOT_FOUND` on the seeded id returns
 *   `{ ...EMPTY, statementNotFound: true }` (mirrors `accountNotFound` /
 *   `notFound`). When absent, `statement` is `null`.
 */
export async function getLedgerSectionState(
  eligible: boolean,
  entryId?: string | null,
  accountCode?: string | null,
  statementId?: string | null,
): Promise<LedgerSectionState> {
  if (!eligible) {
    // Not finance-eligible — never fabricate a cross-tenant call.
    return { ...EMPTY, notEligible: true };
  }

  const id = entryId?.trim() || null;
  const code = accountCode?.trim() || null;
  const sid = statementId?.trim() || null;

  try {
    // Build the base browsable index reads (always seeded when eligible).
    const baseReads = [
      getTrialBalance(),
      listPeriods({ page: 0, size: 20 }),
      listDiscrepancies({ status: 'OPEN', page: 0, size: 20 }),
    ] as const;

    // Collect the id-driven reads (entry, account balance/entries, statement)
    // alongside the base reads in a single Promise.all to minimise latency.
    const idReads: Promise<unknown>[] = [];
    if (id) idReads.push(getJournalEntry(id));
    if (code) {
      idReads.push(getAccountBalance(code));
      idReads.push(getAccountEntries(code, { page: 0, size: 20 }));
    }
    if (sid) idReads.push(getStatement(sid));

    const results = await Promise.all([...baseReads, ...idReads]);
    const [trialBalance, periods, discrepancies, ...idResults] = results as [
      Awaited<ReturnType<typeof getTrialBalance>>,
      Awaited<ReturnType<typeof listPeriods>>,
      Awaited<ReturnType<typeof listDiscrepancies>>,
      ...unknown[]
    ];

    // Unpack id-driven results in the same order they were pushed.
    let cursor = 0;
    const entry = id ? (idResults[cursor++] as Awaited<ReturnType<typeof getJournalEntry>>) : null;
    const accountBalance = code ? (idResults[cursor++] as Awaited<ReturnType<typeof getAccountBalance>>) : null;
    const accountEntries = code ? (idResults[cursor++] as Awaited<ReturnType<typeof getAccountEntries>>) : null;
    const statement = sid ? (idResults[cursor++] as Awaited<ReturnType<typeof getStatement>>) : null;

    return {
      trialBalance,
      periods,
      discrepancies,
      entry,
      accountBalance,
      accountEntries,
      accountNotFound: false,
      statement,
      statementNotFound: false,
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
      // Token not finance-scoped → inline "not available / not scoped".
      return { ...EMPTY, forbidden: true };
    }
    if (
      err instanceof ApiError &&
      err.status === 404 &&
      (err.code === 'JOURNAL_ENTRY_NOT_FOUND' ||
        err.code === 'ACCOUNTING_PERIOD_NOT_FOUND' ||
        err.code === 'RECONCILIATION_DISCREPANCY_NOT_FOUND' ||
        err.code === 'RECONCILIATION_STATEMENT_NOT_FOUND' ||
        err.code === 'LEDGER_ACCOUNT_NOT_FOUND' ||
        err.code.startsWith('HTTP_404'))
    ) {
      // Inline "no such resource" — actionable, no crash. The specific 404
      // flag (notFound vs accountNotFound vs statementNotFound) is determined
      // by the code.
      if (err.code === 'LEDGER_ACCOUNT_NOT_FOUND') {
        return { ...EMPTY, accountNotFound: true };
      }
      if (err.code === 'RECONCILIATION_STATEMENT_NOT_FOUND') {
        return { ...EMPTY, statementNotFound: true };
      }
      return { ...EMPTY, notFound: true };
    }
    if (err instanceof LedgerUnavailableError) {
      // Degrade ONLY the ledger section — shell + IAM / wms / scm /
      // finance-account / erp sections intact.
      return { ...EMPTY, degraded: true };
    }
    // Any other producer error (incl. an unexpected 429 — the ledger has
    // no documented rate-limit, so it falls here, not into a fabricated
    // backoff path) → degrade rather than crash.
    return { ...EMPTY, degraded: true };
  }
}
