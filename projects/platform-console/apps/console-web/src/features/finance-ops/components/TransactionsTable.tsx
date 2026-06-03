'use client';

import { useId, useState } from 'react';
import { Button } from '@/shared/ui/Button';
import {
  formatMoney,
  KNOWN_TXN_STATUSES,
  KNOWN_TXN_TYPES,
  type Transaction,
  type TransactionsQueryParams,
  type TransactionsResponse,
} from '../api/types';
import { useFinanceTransactions } from '../hooks/use-finance-ops';

/**
 * Paginated transactions table (TASK-PC-FE-009 — § 2.4.7).
 *
 * F5 money: every txn's `money` is the producer F5 `{ amount, currency
 * }` — rendered via `formatMoney(...)` ONLY (no `Number()` coercion of
 * `amount`).
 *
 * Honest regulated-state surfacing (§ 2.4.7):
 *   - `FAILED` / `REVERSED` rows are rendered as such, NEVER hidden /
 *     de-emphasised. A red badge surfaces them.
 *   - `reversalOfTransactionId` is surfaced as a column when present
 *     (so the operator can see *what* was reversed).
 *   - `counterpartyAccountId` is surfaced as-is (operator-facing
 *     internal context). Confidential / F7: this value, like every
 *     other txn datum, is never logged.
 *   - Unknown / future `status` or `type` enum values render with a
 *     generic label, never a parser throw (tolerant-parser
 *     discipline).
 *
 * STRICTLY READ-ONLY — no mutation affordance, no confirm dialog, no
 * reason capture, no idempotency.
 */
export interface TransactionsTableProps {
  accountId: string;
  initial: TransactionsResponse;
}

interface FilterState {
  type: string;
  status: string;
}
const EMPTY_FILTERS: FilterState = { type: '', status: '' };

function txnStatusVariant(status: string): 'normal' | 'warn' | 'danger' {
  if (status === 'FAILED' || status === 'REVERSED') return 'danger';
  if (status === 'PENDING') return 'warn';
  return 'normal';
}
const STATUS_CLASS: Record<'normal' | 'warn' | 'danger', string> = {
  normal: 'rounded bg-muted px-1.5 py-0.5 text-xs text-muted-foreground',
  warn: 'rounded bg-amber-100 px-1.5 py-0.5 text-xs text-amber-900 dark:bg-amber-950/60 dark:text-amber-100',
  danger:
    'rounded bg-destructive/15 px-1.5 py-0.5 text-xs text-destructive',
};

function labelForUnknown<T extends string>(
  value: string,
  known: readonly T[],
): string {
  return (known as readonly string[]).includes(value)
    ? value
    : `${value} (unknown)`;
}

export function TransactionsTable({
  accountId,
  initial,
}: TransactionsTableProps) {
  const typeFid = useId();
  const statusFid = useId();

  const [filters, setFilters] = useState<FilterState>(EMPTY_FILTERS);
  const [query, setQuery] = useState<TransactionsQueryParams>({
    page: 0,
    size: initial.meta.size ?? 20,
  });

  const seeded =
    (query.page ?? 0) === 0 && !query.type && !query.status;
  const q = useFinanceTransactions(accountId, query, seeded ? initial : undefined);
  const dataResp = q.data ?? initial;
  const rows: Transaction[] = dataResp.data ?? [];
  const totalElements = dataResp.meta.totalElements ?? rows.length;
  const size = dataResp.meta.size ?? 20;
  const page = dataResp.meta.page ?? query.page ?? 0;
  const totalPages = Math.max(1, Math.ceil(totalElements / Math.max(1, size)));

  function submitFilters(e: React.FormEvent) {
    e.preventDefault();
    setQuery({
      type: filters.type.trim() || undefined,
      status: filters.status.trim() || undefined,
      page: 0,
      size: initial.meta.size ?? 20,
    });
  }

  return (
    <section aria-labelledby="finance-txns-heading">
      <h2
        id="finance-txns-heading"
        className="mb-3 text-lg font-medium text-foreground"
      >
        거래
      </h2>
      <form
        onSubmit={submitFilters}
        className="mb-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4"
        role="search"
        aria-label="거래 목록 필터"
      >
        <div>
          <label
            htmlFor={typeFid}
            className="block text-sm font-medium text-foreground"
          >
            유형
          </label>
          <select
            id={typeFid}
            value={filters.type}
            onChange={(e) =>
              setFilters((f) => ({ ...f, type: e.target.value }))
            }
            data-testid="finance-txn-filter-type"
            className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            <option value="">전체</option>
            {KNOWN_TXN_TYPES.map((t) => (
              <option key={t} value={t}>
                {t}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label
            htmlFor={statusFid}
            className="block text-sm font-medium text-foreground"
          >
            상태
          </label>
          <select
            id={statusFid}
            value={filters.status}
            onChange={(e) =>
              setFilters((f) => ({ ...f, status: e.target.value }))
            }
            data-testid="finance-txn-filter-status"
            className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            <option value="">전체</option>
            {KNOWN_TXN_STATUSES.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </div>
        <div className="flex items-end">
          <Button type="submit" data-testid="finance-txn-filter-submit">
            조회
          </Button>
        </div>
      </form>
      {rows.length === 0 ? (
        <p
          className="mb-6 text-sm text-muted-foreground"
          data-testid="finance-txns-empty"
        >
          표시할 거래가 없습니다.
        </p>
      ) : (
        <>
          <table
            className="mb-3 w-full border-collapse border border-border text-sm"
            data-testid="finance-txns-table"
          >
            <caption className="sr-only">거래 목록</caption>
            <thead>
              <tr className="border-b border-border text-left">
                <th scope="col" className="p-2">
                  거래 ID
                </th>
                <th scope="col" className="p-2">
                  유형
                </th>
                <th scope="col" className="p-2">
                  상태
                </th>
                <th scope="col" className="p-2">
                  금액
                </th>
                <th scope="col" className="p-2">
                  상대 계정
                </th>
                <th scope="col" className="p-2">
                  반전 (reversal)
                </th>
                <th scope="col" className="p-2">
                  생성 (UTC)
                </th>
              </tr>
            </thead>
            <tbody>
              {rows.map((t, i) => {
                const statusVariant = txnStatusVariant(t.status);
                return (
                  <tr
                    key={t.transactionId}
                    data-testid={`finance-txn-row-${i}`}
                    className="border-b border-border"
                  >
                    <td className="p-2">{t.transactionId}</td>
                    <td
                      className="p-2"
                      data-testid={`finance-txn-type-${i}`}
                    >
                      {labelForUnknown(t.type, KNOWN_TXN_TYPES)}
                    </td>
                    <td className="p-2">
                      <span
                        className={STATUS_CLASS[statusVariant]}
                        data-testid={`finance-txn-status-${i}`}
                      >
                        {labelForUnknown(t.status, KNOWN_TXN_STATUSES)}
                      </span>
                    </td>
                    <td
                      className="p-2"
                      data-testid={`finance-txn-amount-${i}`}
                    >
                      {formatMoney(t.money)}
                    </td>
                    <td className="p-2">
                      {t.counterpartyAccountId ?? '—'}
                    </td>
                    <td
                      className="p-2"
                      data-testid={`finance-txn-reversal-${i}`}
                    >
                      {t.reversalOfTransactionId ?? '—'}
                    </td>
                    <td className="p-2">{t.createdAt ?? '—'}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
          <nav
            className="mb-6 flex items-center justify-between"
            aria-label="거래 페이지 이동"
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
              data-testid="finance-txn-prev"
            >
              이전
            </Button>
            <span
              className="text-sm text-muted-foreground"
              data-testid="finance-txn-pageinfo"
            >
              {`${page + 1} / ${totalPages} 페이지 · 총 ${totalElements}건`}
            </span>
            <Button
              variant="secondary"
              disabled={page + 1 >= totalPages}
              onClick={() =>
                setQuery((s) => ({ ...s, page: (s.page ?? 0) + 1 }))
              }
              data-testid="finance-txn-next"
            >
              다음
            </Button>
          </nav>
        </>
      )}
    </section>
  );
}
