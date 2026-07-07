import type { BulkLockItem } from '../api/types';

/**
 * Per-account bulk-lock outcome panel (TASK-PC-FE-210 split of
 * `AccountsScreen`). Pure presentational — renders the partial-failure result
 * list (each account's `LOCKED` / other outcome + optional error code) exactly
 * as the pre-split container did. The partial-failure copy is verbatim: some
 * accounts may fail while the rest are processed.
 */
export function BulkLockResult({ results }: { results: BulkLockItem[] }) {
  return (
    <div
      data-testid="bulk-result"
      className="mb-6 rounded-md border border-border bg-background p-4"
    >
      <h2 className="mb-2 text-sm font-semibold text-foreground">
        일괄 잠금 결과 (계정별)
      </h2>
      <ul className="space-y-1 text-sm">
        {results.map((r) => (
          <li
            key={r.accountId}
            data-testid={`bulk-result-${r.accountId}`}
            className="flex items-center justify-between"
          >
            <span className="font-mono text-xs">{r.accountId}</span>
            <span
              className={
                r.outcome === 'LOCKED'
                  ? 'text-foreground'
                  : 'text-destructive'
              }
            >
              {r.outcome}
              {r.error ? ` — ${r.error.code}` : ''}
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}
