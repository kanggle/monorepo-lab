'use client';

import { useId, useState } from 'react';
import { Button } from '@/shared/ui/Button';
import {
  formatMoney,
  discrepancyMoney,
  KNOWN_DISCREPANCY_TYPES,
  KNOWN_DISCREPANCY_STATUSES,
  type Discrepancy,
  type DiscrepanciesQueryParams,
  type DiscrepanciesResponse,
} from '../api/types';
import { useDiscrepancies } from '../hooks/use-ledger-ops';

/**
 * Reconciliation discrepancy queue (TASK-PC-FE-072 — § 2.4.7.1 /
 * `reconciliation-api.md` § 4).
 *
 * Paginated list with a status filter (OPEN / RESOLVED / all). Each row
 * surfaces the discrepancy type honestly (UNMATCHED_EXTERNAL /
 * UNMATCHED_INTERNAL / AMOUNT_MISMATCH shown as-is; unknown / future →
 * generic label) plus the `externalRef` / `journalEntryId` provenance and
 * the expected / actual amounts (rendered via `discrepancyMoney` +
 * `formatMoney` from the minor-units **string** — F5, no `Number()`
 * coercion). The 11th-increment FX-difference `AMOUNT_MISMATCH` carries
 * BOTH `externalRef` AND `journalEntryId` (the matched pair) — both are
 * surfaced.
 *
 * Selecting a row drives the discrepancy-detail read (lifted via
 * `onSelect`).
 *
 * STRICTLY READ-ONLY — no mutation affordance (no resolve button, no
 * confirm dialog).
 */
export interface DiscrepancyQueueProps {
  initial: DiscrepanciesResponse;
  selectedDiscrepancyId: string | null;
  onSelect: (id: string) => void;
}

function typeLabel(type: string): string {
  return (KNOWN_DISCREPANCY_TYPES as readonly string[]).includes(type)
    ? type
    : `${type} (unknown)`;
}
function statusLabel(status: string): string {
  return (KNOWN_DISCREPANCY_STATUSES as readonly string[]).includes(status)
    ? status
    : `${status} (unknown)`;
}
function statusVariant(status: string): 'normal' | 'warn' {
  if (status === 'OPEN') return 'warn';
  return 'normal';
}
const STATUS_CLASS: Record<'normal' | 'warn', string> = {
  normal: 'rounded bg-muted px-1.5 py-0.5 text-xs text-muted-foreground',
  warn: 'rounded bg-amber-100 px-1.5 py-0.5 text-xs text-amber-900 dark:bg-amber-950/60 dark:text-amber-100',
};

const STATUS_OPTIONS = ['OPEN', 'RESOLVED', ''] as const;

export function DiscrepancyQueue({
  initial,
  selectedDiscrepancyId,
  onSelect,
}: DiscrepancyQueueProps) {
  const statusFid = useId();
  const [status, setStatus] = useState<string>('OPEN');
  const [query, setQuery] = useState<DiscrepanciesQueryParams>({
    status: 'OPEN',
    page: 0,
    size: initial.meta.size ?? 20,
  });

  const seeded =
    (query.page ?? 0) === 0 && query.status === 'OPEN';
  const q = useDiscrepancies(query, seeded ? initial : undefined);
  const dataResp = q.data ?? initial;
  const rows: Discrepancy[] = dataResp.data ?? [];
  const totalElements = dataResp.meta.totalElements ?? rows.length;
  const size = dataResp.meta.size ?? 20;
  const page = dataResp.meta.page ?? query.page ?? 0;
  const totalPages = Math.max(1, Math.ceil(totalElements / Math.max(1, size)));

  function submitFilter(e: React.FormEvent) {
    e.preventDefault();
    setQuery({
      status: status.trim() || undefined,
      page: 0,
      size: initial.meta.size ?? 20,
    });
  }

  return (
    <section aria-labelledby="ledger-recon-heading">
      <h2
        id="ledger-recon-heading"
        className="mb-3 text-lg font-medium text-foreground"
      >
        대사 차이 (reconciliation discrepancies)
      </h2>
      <form
        onSubmit={submitFilter}
        className="mb-4 flex items-end gap-3"
        role="search"
        aria-label="대사 차이 필터"
      >
        <div>
          <label
            htmlFor={statusFid}
            className="block text-sm font-medium text-foreground"
          >
            상태
          </label>
          <select
            id={statusFid}
            value={status}
            onChange={(e) => setStatus(e.target.value)}
            data-testid="ledger-recon-filter-status"
            className="mt-1 rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            {STATUS_OPTIONS.map((s) => (
              <option key={s || 'all'} value={s}>
                {s === '' ? '전체' : s}
              </option>
            ))}
          </select>
        </div>
        <Button type="submit" data-testid="ledger-recon-filter-submit">
          조회
        </Button>
      </form>

      {rows.length === 0 ? (
        <p
          className="mb-4 text-sm text-muted-foreground"
          data-testid="ledger-recon-empty"
        >
          표시할 대사 차이가 없습니다.
        </p>
      ) : (
        <>
          <table className="mb-3 data-table" data-testid="ledger-recon-table">
            <caption className="sr-only">대사 차이 목록</caption>
            <thead>
              <tr className="border-b border-border text-left">
                <th scope="col" className="p-2">
                  유형
                </th>
                <th scope="col" className="p-2">
                  외부 참조 (externalRef)
                </th>
                <th scope="col" className="p-2">
                  분개 ID (journalEntryId)
                </th>
                <th scope="col" className="p-2">
                  기대값 (expected)
                </th>
                <th scope="col" className="p-2">
                  실제값 (actual)
                </th>
                <th scope="col" className="p-2">
                  상태
                </th>
                <th scope="col" className="p-2">
                  조회
                </th>
              </tr>
            </thead>
            <tbody>
              {rows.map((d, i) => {
                const m = discrepancyMoney(d);
                return (
                  <tr
                    key={d.discrepancyId}
                    data-testid={`ledger-recon-row-${i}`}
                    aria-current={
                      selectedDiscrepancyId === d.discrepancyId
                        ? 'true'
                        : undefined
                    }
                    className="border-b border-border"
                  >
                    <td className="p-2" data-testid={`ledger-recon-type-${i}`}>
                      {typeLabel(d.type)}
                    </td>
                    <td
                      className="p-2"
                      data-testid={`ledger-recon-extref-${i}`}
                    >
                      {d.externalRef ?? '—'}
                    </td>
                    <td
                      className="p-2"
                      data-testid={`ledger-recon-journal-${i}`}
                    >
                      {d.journalEntryId ?? '—'}
                    </td>
                    <td
                      className="p-2"
                      data-testid={`ledger-recon-expected-${i}`}
                    >
                      {formatMoney(m.expected)}
                    </td>
                    <td
                      className="p-2"
                      data-testid={`ledger-recon-actual-${i}`}
                    >
                      {formatMoney(m.actual)}
                    </td>
                    <td className="p-2">
                      <span
                        className={STATUS_CLASS[statusVariant(d.status)]}
                        data-testid={`ledger-recon-status-${i}`}
                      >
                        {statusLabel(d.status)}
                      </span>
                    </td>
                    <td className="p-2">
                      <Button
                        variant="secondary"
                        onClick={() => onSelect(d.discrepancyId)}
                        data-testid={`ledger-recon-select-${i}`}
                      >
                        상세
                      </Button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
          <nav
            className="mb-4 flex items-center justify-between"
            aria-label="대사 차이 페이지 이동"
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
              data-testid="ledger-recon-prev"
            >
              이전
            </Button>
            <span
              className="text-sm text-muted-foreground"
              data-testid="ledger-recon-pageinfo"
            >
              {`${page + 1} / ${totalPages} 페이지 · 총 ${totalElements}건`}
            </span>
            <Button
              variant="secondary"
              disabled={page + 1 >= totalPages}
              onClick={() =>
                setQuery((s) => ({ ...s, page: (s.page ?? 0) + 1 }))
              }
              data-testid="ledger-recon-next"
            >
              다음
            </Button>
          </nav>
        </>
      )}
    </section>
  );
}
