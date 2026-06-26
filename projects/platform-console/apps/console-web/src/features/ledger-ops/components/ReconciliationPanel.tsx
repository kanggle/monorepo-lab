'use client';

import { messageForCode } from '@/shared/api/errors';
import type { DiscrepanciesResponse } from '../api/types';
import { DiscrepancyQueue } from './DiscrepancyQueue';
import { DiscrepancyDetail } from './DiscrepancyDetail';
import { StatementLookup } from './StatementLookup';
import { StatementDetail } from './StatementDetail';
import type { useLedgerOpsState } from './use-ledger-ops-state';

type S = ReturnType<typeof useLedgerOpsState>;

/**
 * Reconciliation tab panel content (TASK-PC-FE-134 code split). Extracted
 * verbatim from `LedgerOpsScreen`: the statement lookup + detail (FE-075)
 * ABOVE the discrepancy queue + detail. Loads only when the tab is first
 * activated (or seeded via `?statementId=`). Pure view — state owned by the
 * parent hook.
 */
export function ReconciliationPanel({
  discrepancies,
  selectedStatementId,
  setSelectedStatementId,
  statement,
  statementNotFound,
  selectedDiscrepancyId,
  setSelectedDiscrepancyId,
  handleSelectEntry,
}: {
  discrepancies: DiscrepanciesResponse | null;
} & Pick<
  S,
  | 'selectedStatementId'
  | 'setSelectedStatementId'
  | 'statement'
  | 'statementNotFound'
  | 'selectedDiscrepancyId'
  | 'setSelectedDiscrepancyId'
  | 'handleSelectEntry'
>) {
  return (
    <>
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
    </>
  );
}
