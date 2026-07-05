import { useId } from 'react';
import { Button } from '@/shared/ui/Button';
import { KNOWN_SUGGESTION_STATUSES } from '../api/types';

export interface ReplenishmentFilterState {
  status: string;
  skuCode: string;
}

/**
 * The status / skuCode filter form for {@link ReplenishmentScreen} (TASK-PC-FE-190
 * split). Controlled: the parent owns the filter state + the submit that turns it
 * into the list query. Pure presentation — markup + testids preserved verbatim
 * (`repl-filter-status` / `repl-filter-sku` / `repl-filter-submit`).
 */
export function ReplenishmentFilters({
  filters,
  onChange,
  onSubmit,
}: {
  filters: ReplenishmentFilterState;
  onChange: (next: ReplenishmentFilterState) => void;
  onSubmit: (e: React.FormEvent) => void;
}) {
  const statusFid = useId();
  const skuFid = useId();
  return (
    <form
      onSubmit={onSubmit}
      className="mb-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4"
      role="search"
      aria-label="보충 추천 필터"
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
          value={filters.status}
          onChange={(e) => onChange({ ...filters, status: e.target.value })}
          data-testid="repl-filter-status"
          className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        >
          <option value="">전체</option>
          {KNOWN_SUGGESTION_STATUSES.map((s) => (
            <option key={s} value={s}>
              {s}
            </option>
          ))}
        </select>
      </div>
      <div>
        <label
          htmlFor={skuFid}
          className="block text-sm font-medium text-foreground"
        >
          SKU 코드
        </label>
        <input
          id={skuFid}
          type="text"
          value={filters.skuCode}
          onChange={(e) => onChange({ ...filters, skuCode: e.target.value })}
          data-testid="repl-filter-sku"
          className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        />
      </div>
      <div className="flex items-end">
        <Button type="submit" data-testid="repl-filter-submit">
          조회
        </Button>
      </div>
    </form>
  );
}
