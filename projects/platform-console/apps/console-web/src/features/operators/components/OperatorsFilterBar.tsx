'use client';

import type { FormEvent } from 'react';
import { Button } from '@/shared/ui/Button';
import { OPERATOR_STATUSES, type OperatorStatus } from '../api/types';

/**
 * Status-filter search bar for the operators list (TASK-PC-FE-209 split of
 * `OperatorsTable`). Presentational — the filter value + submit handler live
 * in the `OperatorsScreen` container and arrive via props.
 */
export interface OperatorsFilterBarProps {
  statusFilter: '' | OperatorStatus;
  onStatusFilterChange: (value: '' | OperatorStatus) => void;
  onSubmitFilter: (e: FormEvent) => void;
}

export function OperatorsFilterBar({
  statusFilter,
  onStatusFilterChange,
  onSubmitFilter,
}: OperatorsFilterBarProps) {
  return (
    <form
      onSubmit={onSubmitFilter}
      className="mb-6 flex flex-wrap items-end gap-3"
      role="search"
      aria-label="운영자 목록 필터"
    >
      <div>
        <label
          htmlFor="operators-status-filter"
          className="block text-sm font-medium text-foreground"
        >
          상태 필터
        </label>
        <select
          id="operators-status-filter"
          value={statusFilter}
          onChange={(e) =>
            onStatusFilterChange(e.target.value as '' | OperatorStatus)
          }
          data-testid="operators-status-filter"
          className="mt-1 w-48 rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        >
          <option value="">전체</option>
          {OPERATOR_STATUSES.map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </select>
      </div>
      <Button type="submit" data-testid="operators-filter-submit">
        조회
      </Button>
    </form>
  );
}
