'use client';

import { useRef, useState } from 'react';
import type {
  TrialBalance,
  PeriodsResponse,
  DiscrepanciesResponse,
  JournalEntry,
  AccountBalance,
  AccountEntriesResponse,
  Statement,
} from '../api/types';
import { TrialBalanceTable } from './TrialBalanceTable';
import { PeriodsTable } from './PeriodsTable';
import { PeriodDetail } from './PeriodDetail';
import { JournalEntryLookup } from './JournalEntryLookup';
import { JournalEntryDetail } from './JournalEntryDetail';
import { DiscrepancyQueue } from './DiscrepancyQueue';
import { DiscrepancyDetail } from './DiscrepancyDetail';
import { AccountLookup } from './AccountLookup';
import { AccountDetail } from './AccountDetail';
import { StatementLookup } from './StatementLookup';
import { StatementDetail } from './StatementDetail';
import { PositionLotsLookup } from './PositionLotsLookup';
import { PositionLotsTable } from './PositionLotsTable';
import { FxRatesTable } from './FxRatesTable';
import { FxRateHistoryLookup } from './FxRateHistoryLookup';
import { FxRateHistoryTable } from './FxRateHistoryTable';
import {
  useJournalEntry,
  useAccountBalance,
  useAccountEntries,
  useStatement,
  usePositionLots,
  useFxRates,
  useFxRateHistory,
} from '../hooks/use-ledger-ops';
import { ApiError, messageForCode } from '@/shared/api/errors';
import { FX_HISTORY_DEFAULT_LIMIT } from '../api/types';

/**
 * finance ledger operations section (TASK-PC-FE-072 — § 2.4.7.1; the
 * SECOND finance-product service section bound by the console, alongside
 * the FE-009 account surface).
 *
 * STRICTLY READ-ONLY. A tabbed shell composing four read views:
 *   - Trial Balance — per-account debit/credit + base totals + the honest
 *     double-entry `inBalance` badge;
 *   - Periods — paginated periods list → period detail (close snapshot
 *     when CLOSED; "snapshot 없음 (open)" when OPEN — not an error);
 *   - Journal Entry — entry-id-driven lookup → entry detail (sourceType,
 *     balanced, the F5 multi-currency line triple, revaluation highlight);
 *   - Reconciliation — discrepancy queue (status filter + pagination) →
 *     discrepancy detail (resolution when RESOLVED).
 *
 * Initial seed: server-side via `getLedgerSectionState(eligible, entryId)`
 * (the browsable index reads + the optional id-driven entry); subsequent
 * re-queries (a different period / entry / filter / page) go through the
 * same-origin `/api/ledger/**` proxy via the client hooks.
 *
 * F5 (§ 2.4.7.1, NORMATIVE): every money render goes through
 * `formatMoney(...)` (string-based scale-correct rendering);
 * `exchangeRate` is rendered verbatim. NO `Number()` / `parseFloat()` /
 * `parseInt()` is applied to any `amount` / `exchangeRate` value anywhere
 * in `features/ledger-ops/` (a test grep-asserts this against the on-disk
 * source).
 *
 * The `notFound` (JOURNAL_ENTRY_NOT_FOUND on the seeded entryId) is
 * rendered INLINE inside the Journal Entry tab so the lookup form stays
 * mounted — never as a whole-section block.
 *
 * WCAG AA: the tab strip is an ARIA `tablist` with roving keyboard
 * navigation (ArrowLeft / ArrowRight / Home / End); each panel is an
 * `aria-labelledby` `tabpanel`. Inputs + buttons are native + focusable.
 */

const TABS = [
  { key: 'trial-balance', label: '시산표' },
  { key: 'periods', label: '회계 기간' },
  { key: 'entry', label: '분개 조회' },
  { key: 'reconciliation', label: '대사' },
  { key: 'account', label: '계정' },
  { key: 'lots', label: 'FX 포지션 로트' },
  { key: 'fx-rates', label: 'FX 환율 피드' },
] as const;
type TabKey = (typeof TABS)[number]['key'];

export interface LedgerOpsScreenProps {
  initialEntryId: string | null;
  trialBalance: TrialBalance | null;
  periods: PeriodsResponse | null;
  discrepancies: DiscrepanciesResponse | null;
  initialEntry: JournalEntry | null;
  /** True when the seeded entryId 404'd (JOURNAL_ENTRY_NOT_FOUND) —
   *  rendered inline in the Journal Entry tab; the lookup stays mounted. */
  initialNotFound?: boolean;
  /** The server-seeded account code (TASK-PC-FE-074). */
  initialAccountCode?: string | null;
  /** The server-seeded account balance (TASK-PC-FE-074). */
  initialAccountBalance?: AccountBalance | null;
  /** The server-seeded account entries (TASK-PC-FE-074). */
  initialAccountEntries?: AccountEntriesResponse | null;
  /** True when the seeded accountCode 404'd (LEDGER_ACCOUNT_NOT_FOUND) —
   *  rendered inline in the Account tab; the lookup stays mounted. */
  initialAccountNotFound?: boolean;
  /** The server-seeded statement id (TASK-PC-FE-075). */
  initialStatementId?: string | null;
  /** The server-seeded statement detail (TASK-PC-FE-075). */
  initialStatement?: Statement | null;
  /** True when the seeded statementId 404'd
   *  (RECONCILIATION_STATEMENT_NOT_FOUND) — rendered inline in the 대사
   *  tab; the lookup stays mounted (TASK-PC-FE-075). */
  initialStatementNotFound?: boolean;
}

export function LedgerOpsScreen({
  initialEntryId,
  trialBalance,
  periods,
  discrepancies,
  initialEntry,
  initialNotFound = false,
  initialAccountCode = null,
  initialAccountBalance = null,
  initialAccountEntries = null,
  initialAccountNotFound = false,
  initialStatementId = null,
  initialStatement = null,
  initialStatementNotFound = false,
}: LedgerOpsScreenProps) {
  const [active, setActive] = useState<TabKey>(
    initialAccountCode
      ? 'account'
      : initialEntryId
        ? 'entry'
        : initialStatementId
          ? 'reconciliation'
          : 'trial-balance',
  );
  const tabRefs = useRef<Record<string, HTMLButtonElement | null>>({});

  // Periods + reconciliation row-selection (drives the detail reads).
  const [selectedPeriodId, setSelectedPeriodId] = useState<string | null>(null);
  const [selectedDiscrepancyId, setSelectedDiscrepancyId] = useState<
    string | null
  >(null);

  // Journal entry — entry-id-driven. Seeded from the server when provided.
  const [entryId, setEntryId] = useState<string | null>(initialEntryId);
  const entryQ = useJournalEntry(
    entryId,
    entryId && entryId === initialEntryId ? initialEntry ?? undefined : undefined,
  );
  const entry: JournalEntry | null =
    entryQ.data ?? (entryId === initialEntryId ? initialEntry : null);
  const entryApiErr = entryQ.error instanceof ApiError ? entryQ.error : null;
  // notFound: either the server-seeded 404, or a client lookup 404 — but a
  // freshly-typed id that differs from the seed clears the seeded flag.
  const entryNotFound =
    entryApiErr?.status === 404 ||
    (initialNotFound && entryId === initialEntryId);
  const entryForbidden = entryApiErr?.status === 403;

  // Account — account-code-driven (TASK-PC-FE-074). Seeded from the server
  // when an `?accountCode=` query param is supplied.
  const [selectedAccountCode, setSelectedAccountCode] = useState<string | null>(
    initialAccountCode,
  );
  const accountBalanceQ = useAccountBalance(
    selectedAccountCode,
    selectedAccountCode === initialAccountCode
      ? (initialAccountBalance ?? undefined)
      : undefined,
  );
  const accountEntriesQ = useAccountEntries(
    selectedAccountCode,
    { page: 0, size: 20 },
    selectedAccountCode === initialAccountCode
      ? (initialAccountEntries ?? undefined)
      : undefined,
  );
  const accountBalance: AccountBalance | null =
    accountBalanceQ.data ??
    (selectedAccountCode === initialAccountCode ? initialAccountBalance : null);
  const accountEntries: AccountEntriesResponse | null =
    accountEntriesQ.data ??
    (selectedAccountCode === initialAccountCode ? initialAccountEntries : null);
  const accountApiErr =
    accountBalanceQ.error instanceof ApiError ? accountBalanceQ.error : null;
  const accountNotFound =
    accountApiErr?.status === 404 ||
    (initialAccountNotFound && selectedAccountCode === initialAccountCode);

  // Statement — reconciliation statement id-driven (TASK-PC-FE-075).
  // Seeded from the server when a ?statementId= query param is supplied.
  const [selectedStatementId, setSelectedStatementId] = useState<string | null>(
    initialStatementId,
  );
  const statementQ = useStatement(
    selectedStatementId,
    selectedStatementId === initialStatementId
      ? (initialStatement ?? undefined)
      : undefined,
  );
  const statement: Statement | null =
    statementQ.data ??
    (selectedStatementId === initialStatementId ? initialStatement : null);
  const statementApiErr =
    statementQ.error instanceof ApiError ? statementQ.error : null;
  const statementNotFound =
    statementApiErr?.status === 404 ||
    (initialStatementNotFound && selectedStatementId === initialStatementId);

  // FX position lots — (account, currency)-driven (TASK-PC-FE-091). Purely
  // client-driven: the lookup form submit gates the query (no server seed).
  const [lotsAccountCode, setLotsAccountCode] = useState<string | null>(null);
  const [lotsCurrency, setLotsCurrency] = useState<string | null>(null);
  const lotsQ = usePositionLots(lotsAccountCode, lotsCurrency, true);
  const lotsApiErr = lotsQ.error instanceof ApiError ? lotsQ.error : null;
  const lotsForbidden = lotsApiErr?.status === 403;
  // A 400 (unsupported currency) is the only producer 4xx for this read; an
  // empty position is a 200 (lots: []), NOT a 404. Surface 400 inline.
  const lotsBadRequest = lotsApiErr?.status === 400;

  function handleSubmitLots(code: string, currency: string) {
    setLotsAccountCode(code);
    setLotsCurrency(currency);
  }

  // FX 환율 피드 — global read, no input (TASK-PC-FE-092). Gated on the active
  // tab so a hidden panel never fetches on mount. `rate` stays a string (F5).
  const fxRatesQ = useFxRates(active === 'fx-rates');
  const fxRatesApiErr =
    fxRatesQ.error instanceof ApiError ? fxRatesQ.error : null;
  const fxRatesForbidden = fxRatesApiErr?.status === 403;

  // FX 환율 history 드릴 — per-pair read (TASK-PC-FE-104). Foreign-currency-
  // driven: set either by clicking a feed-table pair OR by the manual lookup
  // form. Gated on the active tab + a non-empty foreign code so a hidden panel
  // (or an unselected pair) never fetches. `rate` stays a string (F5).
  const [fxHistoryForeign, setFxHistoryForeign] = useState<string | null>(null);
  const fxHistoryQ = useFxRateHistory(
    fxHistoryForeign,
    FX_HISTORY_DEFAULT_LIMIT,
    active === 'fx-rates',
  );
  const fxHistoryApiErr =
    fxHistoryQ.error instanceof ApiError ? fxHistoryQ.error : null;
  const fxHistoryForbidden = fxHistoryApiErr?.status === 403;

  /** Called when the operator clicks a trial-balance account code. */
  function handleSelectAccount(code: string) {
    setSelectedAccountCode(code);
    setActive('account');
  }

  /** Called when the operator clicks an entry's entryId in the account
   *  entries table OR a statement match-row journalEntryId — drills into
   *  the Journal Entry tab. */
  function handleSelectEntry(eid: string) {
    setEntryId(eid);
    setActive('entry');
  }

  function onTabKeyDown(e: React.KeyboardEvent, idx: number) {
    let next = idx;
    if (e.key === 'ArrowRight') next = (idx + 1) % TABS.length;
    else if (e.key === 'ArrowLeft') next = (idx - 1 + TABS.length) % TABS.length;
    else if (e.key === 'Home') next = 0;
    else if (e.key === 'End') next = TABS.length - 1;
    else return;
    e.preventDefault();
    const nextKey = TABS[next].key;
    setActive(nextKey);
    tabRefs.current[nextKey]?.focus();
  }

  return (
    <section aria-labelledby="ledger-heading">
      <h1 id="ledger-heading" className="mb-2 text-2xl font-semibold">
        Finance Ledger 운영
      </h1>
      <p className="mb-6 text-sm text-muted-foreground">
        시산표 · 회계 기간 · 분개 · 대사 차이 조회. finance ledger 운영
        표면을 콘솔 안에서 조회하고, OPEN 대사 차이는 직접 해소할 수
        있습니다. 분개 기표 · 기간 마감 등 그 밖의 원장 변경 작업은 콘솔
        범위가 아닙니다.
      </p>

      <div
        role="tablist"
        aria-label="ledger 운영 보기"
        className="mb-6 flex gap-1 border-b border-border"
      >
        {TABS.map((tab, idx) => {
          const selected = active === tab.key;
          return (
            <button
              key={tab.key}
              ref={(el) => {
                tabRefs.current[tab.key] = el;
              }}
              role="tab"
              id={`ledger-tab-${tab.key}`}
              aria-selected={selected}
              aria-controls={`ledger-panel-${tab.key}`}
              tabIndex={selected ? 0 : -1}
              data-testid={`ledger-tab-${tab.key}`}
              onClick={() => setActive(tab.key)}
              onKeyDown={(e) => onTabKeyDown(e, idx)}
              className={
                selected
                  ? 'rounded-t-md border-b-2 border-primary px-3 py-2 text-sm font-medium text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary'
                  : 'rounded-t-md border-b-2 border-transparent px-3 py-2 text-sm text-muted-foreground hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary'
              }
            >
              {tab.label}
            </button>
          );
        })}
      </div>

      {/* Trial Balance panel */}
      <div
        role="tabpanel"
        id="ledger-panel-trial-balance"
        aria-labelledby="ledger-tab-trial-balance"
        hidden={active !== 'trial-balance'}
        data-testid="ledger-panel-trial-balance"
      >
        {trialBalance ? (
          <TrialBalanceTable
            trialBalance={trialBalance}
            onSelectAccount={handleSelectAccount}
          />
        ) : (
          <p
            className="text-sm text-muted-foreground"
            data-testid="ledger-tb-unavailable"
          >
            시산표를 불러올 수 없습니다.
          </p>
        )}
      </div>

      {/* Periods panel */}
      <div
        role="tabpanel"
        id="ledger-panel-periods"
        aria-labelledby="ledger-tab-periods"
        hidden={active !== 'periods'}
        data-testid="ledger-panel-periods"
      >
        {periods ? (
          <>
            <PeriodsTable
              initial={periods}
              selectedPeriodId={selectedPeriodId}
              onSelect={setSelectedPeriodId}
            />
            {selectedPeriodId ? (
              <PeriodDetail periodId={selectedPeriodId} />
            ) : (
              <p
                className="text-sm text-muted-foreground"
                data-testid="ledger-period-none-selected"
              >
                상세를 볼 회계 기간을 선택하세요.
              </p>
            )}
          </>
        ) : (
          <p
            className="text-sm text-muted-foreground"
            data-testid="ledger-periods-unavailable"
          >
            회계 기간을 불러올 수 없습니다.
          </p>
        )}
      </div>

      {/* Journal Entry panel */}
      <div
        role="tabpanel"
        id="ledger-panel-entry"
        aria-labelledby="ledger-tab-entry"
        hidden={active !== 'entry'}
        data-testid="ledger-panel-entry"
      >
        <JournalEntryLookup
          initialEntryId={entryId ?? undefined}
          onSubmit={setEntryId}
        />
        {!entryId ? (
          <p
            className="text-sm text-muted-foreground"
            data-testid="ledger-entry-none"
          >
            조회할 entryId 를 입력하세요.
          </p>
        ) : entryForbidden ? (
          <div
            role="status"
            data-testid="ledger-entry-forbidden"
            className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
          >
            {messageForCode('TENANT_FORBIDDEN')}
          </div>
        ) : entryNotFound ? (
          <div
            role="status"
            data-testid="ledger-entry-not-found"
            className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
          >
            {messageForCode('JOURNAL_ENTRY_NOT_FOUND')}
          </div>
        ) : entry ? (
          <JournalEntryDetail entry={entry} />
        ) : (
          <p
            className="text-sm text-muted-foreground"
            data-testid="ledger-entry-loading"
          >
            불러오는 중…
          </p>
        )}
      </div>

      {/* Reconciliation panel */}
      <div
        role="tabpanel"
        id="ledger-panel-reconciliation"
        aria-labelledby="ledger-tab-reconciliation"
        hidden={active !== 'reconciliation'}
        data-testid="ledger-panel-reconciliation"
      >
        {/* Statement lookup + detail (TASK-PC-FE-075) — ABOVE the discrepancy
            queue. No new tab — wired into the existing 대사 tab. */}
        <StatementLookup
          initialStatementId={selectedStatementId ?? undefined}
          onSubmit={setSelectedStatementId}
        />
        {selectedStatementId ? (
          statementNotFound ? (
            <div
              role="status"
              data-testid="ledger-statement-not-found"
              className="mb-6 rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
            >
              {messageForCode('RECONCILIATION_STATEMENT_NOT_FOUND')}
            </div>
          ) : (
            <StatementDetail
              statement={statement}
              onSelectEntry={handleSelectEntry}
              onSelectDiscrepancy={setSelectedDiscrepancyId}
            />
          )
        ) : null}

        {discrepancies ? (
          <>
            <DiscrepancyQueue
              initial={discrepancies}
              selectedDiscrepancyId={selectedDiscrepancyId}
              onSelect={setSelectedDiscrepancyId}
            />
            {selectedDiscrepancyId ? (
              <DiscrepancyDetail discrepancyId={selectedDiscrepancyId} />
            ) : (
              <p
                className="text-sm text-muted-foreground"
                data-testid="ledger-recon-none-selected"
              >
                상세를 볼 대사 차이를 선택하세요.
              </p>
            )}
          </>
        ) : (
          <p
            className="text-sm text-muted-foreground"
            data-testid="ledger-recon-unavailable"
          >
            대사 차이를 불러올 수 없습니다.
          </p>
        )}
      </div>

      {/* Account panel (TASK-PC-FE-074) */}
      <div
        role="tabpanel"
        id="ledger-panel-account"
        aria-labelledby="ledger-tab-account"
        hidden={active !== 'account'}
        data-testid="ledger-panel-account"
      >
        <AccountLookup
          initialCode={selectedAccountCode ?? undefined}
          onSubmit={setSelectedAccountCode}
        />
        {!selectedAccountCode ? (
          <p
            className="text-sm text-muted-foreground"
            data-testid="ledger-account-none"
          >
            조회할 계정 코드를 입력하거나 시산표의 계정 코드를 클릭하세요.
          </p>
        ) : accountNotFound ? (
          <div
            role="status"
            data-testid="ledger-account-not-found"
            className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
          >
            {messageForCode('LEDGER_ACCOUNT_NOT_FOUND')}
          </div>
        ) : (
          <AccountDetail
            balance={accountBalance}
            entries={accountEntries}
            onSelectEntry={handleSelectEntry}
          />
        )}
      </div>

      {/* FX position lots panel (TASK-PC-FE-091) */}
      <div
        role="tabpanel"
        id="ledger-panel-lots"
        aria-labelledby="ledger-tab-lots"
        hidden={active !== 'lots'}
        data-testid="ledger-panel-lots"
      >
        <PositionLotsLookup
          initialCode={lotsAccountCode ?? undefined}
          initialCurrency={lotsCurrency ?? undefined}
          onSubmit={handleSubmitLots}
        />
        {!lotsAccountCode || !lotsCurrency ? (
          <p
            className="text-sm text-muted-foreground"
            data-testid="ledger-lots-none-input"
          >
            조회할 계정 코드와 통화를 입력하세요.
          </p>
        ) : lotsForbidden ? (
          <div
            role="status"
            data-testid="ledger-lots-forbidden"
            className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
          >
            {messageForCode('TENANT_FORBIDDEN')}
          </div>
        ) : lotsBadRequest ? (
          <div
            role="status"
            data-testid="ledger-lots-bad-request"
            className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
          >
            {messageForCode('VALIDATION_ERROR')}
          </div>
        ) : lotsApiErr ? (
          <div
            role="status"
            data-testid="ledger-lots-error"
            className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
          >
            {messageForCode(lotsApiErr.code)}
          </div>
        ) : (
          <PositionLotsTable
            lots={lotsQ.data ?? null}
            onSelectEntry={handleSelectEntry}
          />
        )}
      </div>

      {/* FX 환율 피드 panel (TASK-PC-FE-092) */}
      <div
        role="tabpanel"
        id="ledger-panel-fx-rates"
        aria-labelledby="ledger-tab-fx-rates"
        hidden={active !== 'fx-rates'}
        data-testid="ledger-panel-fx-rates"
      >
        {fxRatesForbidden ? (
          <div
            role="status"
            data-testid="ledger-fx-rates-forbidden"
            className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
          >
            {messageForCode('TENANT_FORBIDDEN')}
          </div>
        ) : fxRatesApiErr ? (
          <div
            role="status"
            data-testid="ledger-fx-rates-error"
            className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
          >
            {messageForCode(fxRatesApiErr.code)}
          </div>
        ) : (
          <FxRatesTable
            data={fxRatesQ.data ?? null}
            onRefresh={() => void fxRatesQ.refetch()}
            onSelectPair={setFxHistoryForeign}
          />
        )}

        {/* FX 환율 history 드릴 (TASK-PC-FE-104) — per-pair time series, wired
            BELOW the feed table within the SAME tab (NO new tab; same pattern
            as the FE-075 statement drill in the 대사 tab). Set by clicking a
            feed pair above OR the manual lookup below. */}
        <FxRateHistoryLookup
          initialCurrency={fxHistoryForeign ?? undefined}
          onSubmit={setFxHistoryForeign}
        />
        {!fxHistoryForeign ? (
          <p
            className="text-sm text-muted-foreground"
            data-testid="ledger-fx-history-none"
          >
            이력을 볼 외화 통화를 입력하거나 위 피드 표의 통화쌍을 클릭하세요.
          </p>
        ) : fxHistoryForbidden ? (
          <div
            role="status"
            data-testid="ledger-fx-history-forbidden"
            className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
          >
            {messageForCode('TENANT_FORBIDDEN')}
          </div>
        ) : fxHistoryApiErr ? (
          <div
            role="status"
            data-testid="ledger-fx-history-error"
            className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
          >
            {messageForCode(fxHistoryApiErr.code)}
          </div>
        ) : (
          <FxRateHistoryTable
            data={fxHistoryQ.data ?? null}
            onRefresh={() => void fxHistoryQ.refetch()}
          />
        )}
      </div>
    </section>
  );
}
