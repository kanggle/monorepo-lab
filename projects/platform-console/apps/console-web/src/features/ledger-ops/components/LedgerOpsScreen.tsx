'use client';

import dynamic from 'next/dynamic';
import { TrialBalanceTable } from './TrialBalanceTable';
import { LedgerOpsTabs } from './LedgerOpsTabs';
import {
  useLedgerOpsState,
  type LedgerOpsScreenProps,
} from './use-ledger-ops-state';

/**
 * finance ledger operations section (TASK-PC-FE-072 — § 2.4.7.1; the
 * SECOND finance-product service section bound by the console, alongside
 * the FE-009 account surface).
 *
 * STRICTLY READ-ONLY. A tabbed shell composing seven read views:
 *   - Trial Balance — per-account debit/credit + base totals + the honest
 *     double-entry `inBalance` badge;
 *   - Periods — paginated periods list → period detail (close snapshot
 *     when CLOSED; "snapshot 없음 (open)" when OPEN — not an error);
 *   - Journal Entry — entry-id-driven lookup → entry detail (sourceType,
 *     balanced, the F5 multi-currency line triple, revaluation highlight);
 *   - Reconciliation — statement lookup/detail (FE-075) + discrepancy queue
 *     (status filter + pagination) → discrepancy detail;
 *   - Account / FX Position Lots / FX 환율 피드 (FE-074 / 091 / 092 / 104).
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
 * WCAG AA: the tab strip is an ARIA `tablist` with roving keyboard
 * navigation (ArrowLeft / ArrowRight / Home / End); each panel is an
 * `aria-labelledby` `tabpanel`. Inputs + buttons are native + focusable.
 *
 * ── MODULE SPLIT (TASK-PC-FE-106) ── the per-tab state / queries / derived
 * flags / drill handlers live in the `useLedgerOpsState` hook
 * (`use-ledger-ops-state.ts`); the ARIA tab strip + roving keyboard nav live
 * in `LedgerOpsTabs`.
 *
 * ── CODE SPLIT (TASK-PC-FE-134) ── the six NON-default tab panels are
 * `next/dynamic` boundaries, mounted only once their tab has been visited
 * (the `visited` set in `useLedgerOpsState`; the default 시산표 panel and a
 * seeded initial-active panel start visited). On a plain `/ledger` load only
 * the Trial Balance panel ships — the other panels' chunks are fetched on
 * first tab activation. Once mounted a panel stays mounted (`hidden`-toggled),
 * preserving its lookup/query state across tab round-trips. State ownership
 * stays in the parent hook, so the deferred mount never resets a query.
 */

export type { LedgerOpsScreenProps };

function PanelLoading() {
  return (
    <p
      role="status"
      className="text-sm text-muted-foreground"
      data-testid="ledger-panel-loading"
    >
      불러오는 중…
    </p>
  );
}

const PeriodsPanel = dynamic(
  () => import('./PeriodsPanel').then((m) => m.PeriodsPanel),
  { ssr: false, loading: PanelLoading },
);
const JournalEntryPanel = dynamic(
  () => import('./JournalEntryPanel').then((m) => m.JournalEntryPanel),
  { ssr: false, loading: PanelLoading },
);
const ReconciliationPanel = dynamic(
  () => import('./ReconciliationPanel').then((m) => m.ReconciliationPanel),
  { ssr: false, loading: PanelLoading },
);
const AccountPanel = dynamic(
  () => import('./AccountPanel').then((m) => m.AccountPanel),
  { ssr: false, loading: PanelLoading },
);
const LotsPanel = dynamic(
  () => import('./LotsPanel').then((m) => m.LotsPanel),
  { ssr: false, loading: PanelLoading },
);
const FxRatesPanel = dynamic(
  () => import('./FxRatesPanel').then((m) => m.FxRatesPanel),
  { ssr: false, loading: PanelLoading },
);

export function LedgerOpsScreen(props: LedgerOpsScreenProps) {
  const { trialBalance, periods, discrepancies } = props;
  const state = useLedgerOpsState(props);
  const { active, setActive, visited } = state;

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

      <LedgerOpsTabs active={active} onSelect={setActive} />

      {/* Trial Balance panel — the default tab, kept static (always the first
          paint, so no benefit to deferring it). */}
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
            onSelectAccount={state.handleSelectAccount}
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

      {/* Periods panel (lazy — mounted on first visit) */}
      <div
        role="tabpanel"
        id="ledger-panel-periods"
        aria-labelledby="ledger-tab-periods"
        hidden={active !== 'periods'}
        data-testid="ledger-panel-periods"
      >
        {visited.has('periods') ? (
          <PeriodsPanel
            periods={periods}
            selectedPeriodId={state.selectedPeriodId}
            setSelectedPeriodId={state.setSelectedPeriodId}
          />
        ) : null}
      </div>

      {/* Journal Entry panel (lazy) */}
      <div
        role="tabpanel"
        id="ledger-panel-entry"
        aria-labelledby="ledger-tab-entry"
        hidden={active !== 'entry'}
        data-testid="ledger-panel-entry"
      >
        {visited.has('entry') ? (
          <JournalEntryPanel
            entryId={state.entryId}
            setEntryId={state.setEntryId}
            entryForbidden={state.entryForbidden}
            entryNotFound={state.entryNotFound}
            entry={state.entry}
          />
        ) : null}
      </div>

      {/* Reconciliation panel (lazy) */}
      <div
        role="tabpanel"
        id="ledger-panel-reconciliation"
        aria-labelledby="ledger-tab-reconciliation"
        hidden={active !== 'reconciliation'}
        data-testid="ledger-panel-reconciliation"
      >
        {visited.has('reconciliation') ? (
          <ReconciliationPanel
            discrepancies={discrepancies}
            selectedStatementId={state.selectedStatementId}
            setSelectedStatementId={state.setSelectedStatementId}
            statement={state.statement}
            statementNotFound={state.statementNotFound}
            selectedDiscrepancyId={state.selectedDiscrepancyId}
            setSelectedDiscrepancyId={state.setSelectedDiscrepancyId}
            handleSelectEntry={state.handleSelectEntry}
          />
        ) : null}
      </div>

      {/* Account panel (TASK-PC-FE-074) (lazy) */}
      <div
        role="tabpanel"
        id="ledger-panel-account"
        aria-labelledby="ledger-tab-account"
        hidden={active !== 'account'}
        data-testid="ledger-panel-account"
      >
        {visited.has('account') ? (
          <AccountPanel
            selectedAccountCode={state.selectedAccountCode}
            setSelectedAccountCode={state.setSelectedAccountCode}
            accountNotFound={state.accountNotFound}
            accountBalance={state.accountBalance}
            accountEntries={state.accountEntries}
            handleSelectEntry={state.handleSelectEntry}
          />
        ) : null}
      </div>

      {/* FX position lots panel (TASK-PC-FE-091) (lazy) */}
      <div
        role="tabpanel"
        id="ledger-panel-lots"
        aria-labelledby="ledger-tab-lots"
        hidden={active !== 'lots'}
        data-testid="ledger-panel-lots"
      >
        {visited.has('lots') ? (
          <LotsPanel
            lotsAccountCode={state.lotsAccountCode}
            lotsCurrency={state.lotsCurrency}
            lotsForbidden={state.lotsForbidden}
            lotsBadRequest={state.lotsBadRequest}
            lotsApiErr={state.lotsApiErr}
            lotsQ={state.lotsQ}
            handleSubmitLots={state.handleSubmitLots}
            handleSelectEntry={state.handleSelectEntry}
          />
        ) : null}
      </div>

      {/* FX 환율 피드 panel (TASK-PC-FE-092) (lazy) */}
      <div
        role="tabpanel"
        id="ledger-panel-fx-rates"
        aria-labelledby="ledger-tab-fx-rates"
        hidden={active !== 'fx-rates'}
        data-testid="ledger-panel-fx-rates"
      >
        {visited.has('fx-rates') ? (
          <FxRatesPanel
            fxRatesForbidden={state.fxRatesForbidden}
            fxRatesApiErr={state.fxRatesApiErr}
            fxRatesRefreshError={state.fxRatesRefreshError}
            fxRatesQ={state.fxRatesQ}
            refreshFxRatesMutation={state.refreshFxRatesMutation}
            fxRatesRefreshing={state.fxRatesRefreshing}
            setFxHistoryForeign={state.setFxHistoryForeign}
            fxHistoryForeign={state.fxHistoryForeign}
            fxHistoryForbidden={state.fxHistoryForbidden}
            fxHistoryApiErr={state.fxHistoryApiErr}
            fxHistoryQ={state.fxHistoryQ}
          />
        ) : null}
      </div>
    </section>
  );
}
