'use client';

import {
  formatMoney,
  KNOWN_PERIOD_STATUSES,
  periodStatusTone,
  type Period,
} from '../api/types';
import { usePeriod } from '../hooks/use-ledger-ops';
import { StatusBadge } from '@/shared/ui/StatusBadge';
import { labelForUnknown } from '@/shared/lib/tolerant-label';

/**
 * Accounting period detail (TASK-PC-FE-072 — § 2.4.7.1 / `ledger-api.md`
 * § 8).
 *
 * The detail read of a CLOSED period carries an immutable close
 * `snapshot` (per-account debit/credit + grand totals + the
 * double-entry `inBalance`), rendered here via `formatMoney(...)` ONLY
 * (F5 — no `Number()` coercion of `amount`). An OPEN period has NO
 * snapshot — `snapshot: null` is NOT an error; the detail shows a
 * "snapshot 없음 (open)" notice (honest absence, never a crash).
 *
 * STRICTLY READ-ONLY — no mutation affordance.
 */
export interface PeriodDetailProps {
  periodId: string;
  /** Optional server-seeded period (when the detail was fetched
   *  server-side); otherwise the hook fetches via the proxy. */
  initial?: Period | null;
}

export function PeriodDetail({ periodId, initial }: PeriodDetailProps) {
  const q = usePeriod(periodId);
  const period: Period | null =
    q.data ?? (initial && initial.periodId === periodId ? initial : null);

  if (!period) {
    return (
      <section
        aria-labelledby="ledger-period-detail-heading"
        className="mb-6 rounded-md border border-border bg-background p-4"
        data-testid="ledger-period-detail"
      >
        <h3
          id="ledger-period-detail-heading"
          className="mb-2 text-base font-medium text-foreground"
        >
          기간 상세
        </h3>
        <p
          className="text-sm text-muted-foreground"
          data-testid="ledger-period-detail-loading"
        >
          불러오는 중…
        </p>
      </section>
    );
  }

  const snapshot = period.snapshot ?? null;

  return (
    <section
      aria-labelledby="ledger-period-detail-heading"
      className="mb-6 rounded-md border border-border bg-background p-4"
      data-testid="ledger-period-detail"
    >
      <h3
        id="ledger-period-detail-heading"
        className="mb-3 text-base font-medium text-foreground"
      >
        기간 상세 — {period.periodId}
      </h3>
      <dl className="mb-4 grid grid-cols-2 gap-3 text-sm">
        <div>
          <dt className="text-muted-foreground">상태</dt>
          <dd
            className="text-foreground"
            data-testid="ledger-period-detail-status"
          >
            <StatusBadge tone={periodStatusTone(period.status)}>
              {labelForUnknown(period.status, KNOWN_PERIOD_STATUSES)}
            </StatusBadge>
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">기간</dt>
          <dd className="text-foreground">
            {`${period.from ?? '—'} → ${period.to ?? '—'}`}
          </dd>
        </div>
      </dl>

      {snapshot ? (
        <div data-testid="ledger-period-snapshot">
          <div className="mb-2 flex items-center gap-3">
            <h4 className="text-sm font-medium text-foreground">
              마감 스냅샷 (close snapshot)
            </h4>
            <span
              data-testid="ledger-snapshot-inbalance"
              className={
                snapshot.inBalance
                  ? 'rounded bg-muted px-1.5 py-0.5 text-xs text-muted-foreground'
                  : 'rounded bg-destructive/15 px-1.5 py-0.5 text-xs text-destructive'
              }
            >
              {snapshot.inBalance
                ? '대차 일치 (in balance)'
                : '대차 불일치 (out of balance)'}
            </span>
          </div>
          <table className="data-table" data-testid="ledger-snapshot-table">
            <caption className="sr-only">마감 스냅샷</caption>
            <thead>
              <tr className="border-b border-border text-left">
                <th scope="col" className="p-2">
                  계정 코드
                </th>
                <th scope="col" className="p-2">
                  차변 (debit)
                </th>
                <th scope="col" className="p-2">
                  대변 (credit)
                </th>
              </tr>
            </thead>
            <tbody>
              {snapshot.accounts.map((a, i) => (
                <tr
                  key={`${a.ledgerAccountCode}-${i}`}
                  data-testid={`ledger-snapshot-row-${i}`}
                  className="border-b border-border"
                >
                  <td className="p-2">{a.ledgerAccountCode}</td>
                  <td className="p-2">{formatMoney(a.debitTotal)}</td>
                  <td className="p-2">{formatMoney(a.creditTotal)}</td>
                </tr>
              ))}
              <tr className="border-t-2 border-border font-medium">
                <td className="p-2">합계 (grand total)</td>
                <td className="p-2" data-testid="ledger-snapshot-grand-debit">
                  {formatMoney(snapshot.grandDebitTotal)}
                </td>
                <td
                  className="p-2"
                  data-testid="ledger-snapshot-grand-credit"
                >
                  {formatMoney(snapshot.grandCreditTotal)}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      ) : (
        <p
          className="text-sm text-muted-foreground"
          data-testid="ledger-period-no-snapshot"
        >
          snapshot 없음 (open) — 마감되지 않은 기간에는 스냅샷이 없습니다.
        </p>
      )}
    </section>
  );
}
