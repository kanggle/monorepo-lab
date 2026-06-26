import { messageForCode } from '@/shared/api/errors';
import { PositionLotsLookup } from './PositionLotsLookup';
import { PositionLotsTable } from './PositionLotsTable';
import type { useLedgerOpsState } from './use-ledger-ops-state';

type S = ReturnType<typeof useLedgerOpsState>;

/**
 * FX position lots tab panel content (TASK-PC-FE-134 code split,
 * TASK-PC-FE-091 surface). Extracted verbatim from `LedgerOpsScreen`;
 * (account, currency)-driven lookup + lots table load only when the tab is
 * first activated. Pure view — state owned by the parent hook.
 */
export function LotsPanel({
  lotsAccountCode,
  lotsCurrency,
  lotsForbidden,
  lotsBadRequest,
  lotsApiErr,
  lotsQ,
  handleSubmitLots,
  handleSelectEntry,
}: Pick<
  S,
  | 'lotsAccountCode'
  | 'lotsCurrency'
  | 'lotsForbidden'
  | 'lotsBadRequest'
  | 'lotsApiErr'
  | 'lotsQ'
  | 'handleSubmitLots'
  | 'handleSelectEntry'
>) {
  return (
    <>
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
    </>
  );
}
