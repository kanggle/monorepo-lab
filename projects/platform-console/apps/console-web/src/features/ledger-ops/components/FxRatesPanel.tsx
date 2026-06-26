'use client';

import { messageForCode } from '@/shared/api/errors';
import { FxRatesTable } from './FxRatesTable';
import { FxRateHistoryLookup } from './FxRateHistoryLookup';
import { FxRateHistoryTable } from './FxRateHistoryTable';
import type { useLedgerOpsState } from './use-ledger-ops-state';

type S = ReturnType<typeof useLedgerOpsState>;

/**
 * FX 환율 피드 tab panel content (TASK-PC-FE-134 code split,
 * TASK-PC-FE-092 + FE-104 surfaces). Extracted verbatim from
 * `LedgerOpsScreen`: the feed table + manual refresh ABOVE the per-pair
 * history drill. Loads only when the tab is first activated. Pure view —
 * state owned by the parent hook (the feed/history queries gate on the active
 * tab there).
 */
export function FxRatesPanel({
  fxRatesForbidden,
  fxRatesApiErr,
  fxRatesRefreshError,
  fxRatesQ,
  refreshFxRatesMutation,
  fxRatesRefreshing,
  setFxHistoryForeign,
  fxHistoryForeign,
  fxHistoryForbidden,
  fxHistoryApiErr,
  fxHistoryQ,
}: Pick<
  S,
  | 'fxRatesForbidden'
  | 'fxRatesApiErr'
  | 'fxRatesRefreshError'
  | 'fxRatesQ'
  | 'refreshFxRatesMutation'
  | 'fxRatesRefreshing'
  | 'setFxHistoryForeign'
  | 'fxHistoryForeign'
  | 'fxHistoryForbidden'
  | 'fxHistoryApiErr'
  | 'fxHistoryQ'
>) {
  return (
    <>
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
    </>
  );
}
