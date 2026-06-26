'use client';

import type { PeriodsResponse } from '../api/types';
import { PeriodsTable } from './PeriodsTable';
import { PeriodDetail } from './PeriodDetail';
import type { useLedgerOpsState } from './use-ledger-ops-state';

type S = ReturnType<typeof useLedgerOpsState>;

/**
 * Periods tab panel content (TASK-PC-FE-134 code split). Extracted verbatim
 * from `LedgerOpsScreen` so it can be lazy-loaded via `next/dynamic`; the
 * periods list + selected-period detail load only when the tab is first
 * activated. State is owned by the parent `useLedgerOpsState` hook and passed
 * in — this is a pure view.
 */
export function PeriodsPanel({
  periods,
  selectedPeriodId,
  setSelectedPeriodId,
}: {
  periods: PeriodsResponse | null;
} & Pick<S, 'selectedPeriodId' | 'setSelectedPeriodId'>) {
  if (!periods) {
    return (
      <p
        className="text-sm text-muted-foreground"
        data-testid="ledger-periods-unavailable"
      >
        회계 기간을 불러올 수 없습니다.
      </p>
    );
  }
  return (
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
  );
}
