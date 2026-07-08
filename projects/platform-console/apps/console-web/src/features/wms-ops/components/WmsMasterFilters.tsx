'use client';

import type { Dispatch, FormEvent, SetStateAction } from 'react';
import { Button } from '@/shared/ui/Button';
import type { MasterFilterState } from './wms-master-helpers';

/**
 * The `q`(검색어)/`status`(상태) filter form for {@link WmsMasterTable}
 * (TASK-PC-FE-223) — mirrors `WmsInboundFilters`'s controlled-input +
 * submit shape. `status` is a free-text input (NOT a hardcoded `<select>`
 * enum, task Edge Case: "status 필터 옵션은 `_meta/enums` 가 아닌 read-model
 * 실제 값 기반" — the producer's per-type status vocabulary is not
 * enumerated by the contract). Controlled: the container owns the filter
 * state + the submit that turns it into the list query.
 */
export interface WmsMasterFiltersProps {
  qFid: string;
  statusFid: string;
  filters: MasterFilterState;
  onFiltersChange: Dispatch<SetStateAction<MasterFilterState>>;
  onSubmit: (e: FormEvent) => void;
}

export function WmsMasterFilters({
  qFid,
  statusFid,
  filters,
  onFiltersChange,
  onSubmit,
}: WmsMasterFiltersProps) {
  return (
    <form
      onSubmit={onSubmit}
      className="mb-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4"
      role="search"
      aria-label="마스터 참조 데이터 필터"
    >
      <div>
        <label
          htmlFor={qFid}
          className="block text-sm font-medium text-foreground"
        >
          검색어
        </label>
        <input
          id={qFid}
          type="text"
          value={filters.q}
          onChange={(e) =>
            onFiltersChange((f) => ({ ...f, q: e.target.value }))
          }
          data-testid="wms-master-filter-q"
          className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        />
      </div>
      <div>
        <label
          htmlFor={statusFid}
          className="block text-sm font-medium text-foreground"
        >
          상태
        </label>
        <input
          id={statusFid}
          type="text"
          value={filters.status}
          onChange={(e) =>
            onFiltersChange((f) => ({ ...f, status: e.target.value }))
          }
          data-testid="wms-master-filter-status"
          className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        />
      </div>
      <div className="flex items-end">
        <Button type="submit" data-testid="wms-master-filter-submit">
          조회
        </Button>
      </div>
    </form>
  );
}
