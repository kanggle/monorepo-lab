'use client';

import { useState } from 'react';
import { Button } from '@/shared/ui/Button';
import { StatusBadge } from '@/shared/ui/StatusBadge';
import { labelForUnknown } from '@/shared/lib/tolerant-label';
import {
  KNOWN_PERIOD_STATUSES,
  periodStatusTone,
  type Period,
  type PeriodsQueryParams,
  type PeriodsResponse,
} from '../api/types';
import { usePeriods } from '../hooks/use-ledger-ops';

/**
 * Accounting periods table (TASK-PC-FE-072 — § 2.4.7.1).
 *
 * Paginated list of periods (`ledger-api.md` § 7). Each row surfaces the
 * period status honestly (OPEN / CLOSED shown as-is; an unknown / future
 * status renders with a generic label, never a parser throw). Selecting a
 * row drives the period-detail read (lifted to the screen via
 * `onSelect`).
 *
 * STRICTLY READ-ONLY — no mutation affordance (no close-period button, no
 * confirm dialog).
 */
export interface PeriodsTableProps {
  initial: PeriodsResponse;
  selectedPeriodId: string | null;
  onSelect: (periodId: string) => void;
}

export function PeriodsTable({
  initial,
  selectedPeriodId,
  onSelect,
}: PeriodsTableProps) {
  const [query, setQuery] = useState<PeriodsQueryParams>({
    page: 0,
    size: initial.meta.size ?? 20,
  });

  const q = usePeriods(query, (query.page ?? 0) === 0 ? initial : undefined);
  const dataResp = q.data ?? initial;
  const rows: Period[] = dataResp.data ?? [];
  const totalElements = dataResp.meta.totalElements ?? rows.length;
  const size = dataResp.meta.size ?? 20;
  const page = dataResp.meta.page ?? query.page ?? 0;
  const totalPages = Math.max(1, Math.ceil(totalElements / Math.max(1, size)));

  return (
    <section aria-labelledby="ledger-periods-heading">
      <h2
        id="ledger-periods-heading"
        className="mb-3 text-lg font-medium text-foreground"
      >
        회계 기간 (accounting periods)
      </h2>
      {rows.length === 0 ? (
        <p
          className="mb-4 text-sm text-muted-foreground"
          data-testid="ledger-periods-empty"
        >
          표시할 회계 기간이 없습니다.
        </p>
      ) : (
        <>
          <table
            className="mb-3 data-table"
            data-testid="ledger-periods-table"
          >
            <caption className="sr-only">회계 기간 목록</caption>
            <thead>
              <tr className="border-b border-border text-left">
                <th scope="col" className="p-2">
                  기간 ID
                </th>
                <th scope="col" className="p-2">
                  상태
                </th>
                <th scope="col" className="p-2">
                  시작
                </th>
                <th scope="col" className="p-2">
                  종료
                </th>
                <th scope="col" className="p-2">
                  분개 수
                </th>
                <th scope="col" className="p-2">
                  조회
                </th>
              </tr>
            </thead>
            <tbody>
              {rows.map((p, i) => (
                <tr
                  key={p.periodId}
                  data-testid={`ledger-period-row-${i}`}
                  aria-current={
                    selectedPeriodId === p.periodId ? 'true' : undefined
                  }
                  className="border-b border-border"
                >
                  <td className="p-2">{p.periodId}</td>
                  <td className="p-2">
                    <StatusBadge
                      tone={periodStatusTone(p.status)}
                      data-testid={`ledger-period-status-${i}`}
                    >
                      {labelForUnknown(p.status, KNOWN_PERIOD_STATUSES)}
                    </StatusBadge>
                  </td>
                  <td className="p-2">{p.from ?? '—'}</td>
                  <td className="p-2">{p.to ?? '—'}</td>
                  <td className="p-2">{p.entryCount ?? '—'}</td>
                  <td className="p-2">
                    <Button
                      variant="secondary"
                      onClick={() => onSelect(p.periodId)}
                      data-testid={`ledger-period-select-${i}`}
                    >
                      상세
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <nav
            className="mb-4 flex items-center justify-between"
            aria-label="회계 기간 페이지 이동"
          >
            <Button
              variant="secondary"
              disabled={(query.page ?? 0) <= 0}
              onClick={() =>
                setQuery((s) => ({
                  ...s,
                  page: Math.max(0, (s.page ?? 0) - 1),
                }))
              }
              data-testid="ledger-periods-prev"
            >
              이전
            </Button>
            <span
              className="text-sm text-muted-foreground"
              data-testid="ledger-periods-pageinfo"
            >
              {`${page + 1} / ${totalPages} 페이지 · 총 ${totalElements}건`}
            </span>
            <Button
              variant="secondary"
              disabled={page + 1 >= totalPages}
              onClick={() =>
                setQuery((s) => ({ ...s, page: (s.page ?? 0) + 1 }))
              }
              data-testid="ledger-periods-next"
            >
              다음
            </Button>
          </nav>
        </>
      )}
    </section>
  );
}
