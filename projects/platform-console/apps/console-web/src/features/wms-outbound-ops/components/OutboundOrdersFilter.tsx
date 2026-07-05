'use client';

import type { FormEvent } from 'react';
import { Button } from '@/shared/ui/Button';
import { STATUS_FILTER_OPTIONS } from './outbound-ops-helpers';

/**
 * The 출고 주문 status filter form for {@link OutboundOrdersTable}
 * (TASK-PC-FE-198 split) — the status `<select>` (STATUS_FILTER_OPTIONS) + the
 * 조회 submit. Controlled: the `OutboundOpsScreen` container owns the filter
 * state + the submit that turns it into the list query. Pure presentation —
 * markup + testids preserved verbatim (`outbound-status-filter`,
 * `outbound-filter-submit`).
 */
export interface OutboundOrdersFilterProps {
  statusFid: string;
  statusFilter: string;
  onStatusFilterChange: (value: string) => void;
  onSubmitFilter: (e: FormEvent) => void;
}

export function OutboundOrdersFilter({
  statusFid,
  statusFilter,
  onStatusFilterChange,
  onSubmitFilter,
}: OutboundOrdersFilterProps) {
  return (
    <form
      onSubmit={onSubmitFilter}
      className="mb-4 flex flex-wrap items-end gap-3"
      role="search"
      aria-label="출고 주문 필터"
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
          value={statusFilter}
          onChange={(e) => onStatusFilterChange(e.target.value)}
          data-testid="outbound-status-filter"
          className="mt-1 rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        >
          {STATUS_FILTER_OPTIONS.map((s) => (
            <option key={s || 'all'} value={s}>
              {s || '전체'}
            </option>
          ))}
        </select>
      </div>
      <Button type="submit" data-testid="outbound-filter-submit">
        조회
      </Button>
    </form>
  );
}
