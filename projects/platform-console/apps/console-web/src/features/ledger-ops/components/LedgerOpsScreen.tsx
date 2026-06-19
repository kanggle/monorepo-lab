'use client';

import { messageForCode } from '@/shared/api/errors';
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
 *
 * ── MODULE SPLIT (TASK-PC-FE-106) ── the per-tab state / queries / derived
 * flags / drill handlers live in the `useLedgerOpsState` hook
 * (`use-ledger-ops-state.ts`); the ARIA tab strip + roving keyboard nav live
 * in `LedgerOpsTabs`. This component is the pure view: it calls the hook and
 * renders the tab strip + the seven `tabpanel`s. (Each tab's content was
 * already its own sub-component — TrialBalanceTable / PeriodsTable / … .)
 */

export type { LedgerOpsScreenProps };

export function LedgerOpsScreen(props: LedgerOpsScreenProps) {
  const { trialBalance, periods, discrepancies } = props;
  const {
    active,
    setActive,
    selectedPeriodId,
    setSelectedPeriodId,
    selectedDiscrepancyId,
    setSelectedDiscrepancyId,
    entryId,
    setEntryId,
    entry,
    entryNotFound,
    entryForbidden,
    selectedAccountCode,
    setSelectedAccountCode,
    accountBalance,
    accountEntries,
    accountNotFound,
    selectedStatementId,
    setSelectedStatementId,
    statement,
    statementNotFound,
    lotsAccountCode,
    lotsCurrency,
    lotsQ,
    lotsForbidden,
    lotsBadRequest,
    lotsApiErr,
    handleSubmitLots,
    fxRatesQ,
    fxRatesForbidden,
    fxRatesApiErr,
    refreshFxRatesMutation,
    fxRatesRefreshing,
    fxRatesRefreshError,
    fxHistoryForeign,
    setFxHistoryForeign,
    fxHistoryQ,
    fxHistoryForbidden,
    fxHistoryApiErr,
    handleSelectAccount,
    handleSelectEntry,
  } = useLedgerOpsState(props);

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
          <>
            {fxRatesRefreshError ? (
              <div
                role="status"
                data-testid="ledger-fx-rates-refresh-error"
                className="mb-3 rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
              >
                {messageForCode(fxRatesRefreshError.code)}
              </div>
            ) : null}
            <FxRatesTable
              data={fxRatesQ.data ?? null}
              onRefresh={() => void refreshFxRatesMutation.mutate()}
              refreshing={fxRatesRefreshing}
              onSelectPair={setFxHistoryForeign}
            />
          </>
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
