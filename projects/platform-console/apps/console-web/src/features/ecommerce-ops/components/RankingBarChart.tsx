import type { CellStatus, RankedEntry } from '../api/overview-state';

/**
 * Top-N horizontal ranking bar chart (TASK-PC-FE-170). Pure CSS/flex — the
 * project has NO chart library and `EcommerceOverview` is strictly server
 * rendered, so this stays a server component (NO `'use client'`, no recharts).
 *
 * Degrade-safe: a non-`ok` status renders a compact "권한 없음" / "데이터를
 * 불러올 수 없습니다" placeholder; an empty ranking renders "데이터가 없습니다.".
 * Each bar is proportional to the max value in the chart (min 2% sliver for a
 * non-zero value; guarded against divide-by-zero). Bars are decorative
 * (`aria-hidden`) — the value text carries the number; the list is labelled by
 * its title for AT.
 */

function placeholder(status: CellStatus): string {
  return status === 'forbidden'
    ? '권한 없음'
    : '데이터를 불러올 수 없습니다';
}

function formatValue(value: number, format: 'count' | 'currency'): string {
  return format === 'currency'
    ? `₩${value.toLocaleString('ko-KR')}`
    : `${value.toLocaleString()}건`;
}

export function RankingBarChart({
  title,
  entries,
  status,
  format,
  testid,
}: {
  title: string;
  entries: RankedEntry[];
  status: CellStatus;
  format: 'count' | 'currency';
  testid: string;
}) {
  if (status !== 'ok') {
    return (
      <div
        data-testid={testid}
        className="rounded-md border border-border bg-background p-4"
      >
        <h4 className="mb-3 text-sm font-semibold text-foreground">{title}</h4>
        <p className="text-sm text-muted-foreground">{placeholder(status)}</p>
      </div>
    );
  }

  if (entries.length === 0) {
    return (
      <div
        data-testid={testid}
        className="rounded-md border border-border bg-background p-4"
      >
        <h4 className="mb-3 text-sm font-semibold text-foreground">{title}</h4>
        <p className="text-sm text-muted-foreground">데이터가 없습니다.</p>
      </div>
    );
  }

  const maxValue = Math.max(...entries.map((e) => e.value));

  return (
    <div
      data-testid={testid}
      className="rounded-md border border-border bg-background p-4"
    >
      <h4 className="mb-3 text-sm font-semibold text-foreground">{title}</h4>
      <dl className="space-y-2 text-sm" aria-label={title}>
        {entries.map((e) => {
          const pct = maxValue > 0 ? Math.max(2, (e.value / maxValue) * 100) : 0;
          return (
            <div
              key={e.id}
              data-testid={`${testid}-row-${e.id}`}
              className="flex items-center gap-3"
            >
              <dt className="min-w-0 flex-1 truncate text-foreground">
                {e.label}
              </dt>
              <div className="h-2 w-24 shrink-0 overflow-hidden rounded bg-muted">
                <div
                  className="bg-primary/70 h-2 rounded"
                  style={{ width: `${pct}%` }}
                  aria-hidden="true"
                />
              </div>
              <dd className="shrink-0 tabular-nums text-foreground">
                {formatValue(e.value, format)}
              </dd>
            </div>
          );
        })}
      </dl>
    </div>
  );
}
