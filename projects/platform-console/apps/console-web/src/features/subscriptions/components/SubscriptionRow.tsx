'use client';

import type { DomainSubscriptionRow } from '../lib/derive';
import type { PendingAction } from './subscriptions-actions';

/**
 * Catalog-derived domain subscription row (TASK-PC-FE-211 split from
 * `SubscriptionsScreen`). Presentational only — renders the domain label, the
 * ACTIVE/미구독 status badge, and the state-gated action buttons (ACTIVE →
 * suspend/cancel, else subscribe). All orchestration (the confirm dialog,
 * mutation lifecycle, refresh) stays in the container and is reached via
 * `onOpen`.
 */
export function SubscriptionRow({
  row,
  onOpen,
}: {
  row: DomainSubscriptionRow;
  onOpen: (a: PendingAction) => void;
}) {
  return (
    <li
      data-testid={`subscription-row-${row.key}`}
      className="flex items-center justify-between gap-4 px-4 py-4"
    >
      <div className="min-w-0">
        <p className="truncate text-sm font-medium text-foreground">
          {row.label}
        </p>
        <p
          data-testid={`subscription-status-${row.key}`}
          className={
            row.state === 'ACTIVE'
              ? 'mt-0.5 text-xs font-medium text-emerald-600 dark:text-emerald-400'
              : 'mt-0.5 text-xs text-muted-foreground'
          }
        >
          {row.state === 'ACTIVE' ? '● 구독 중' : '○ 미구독'}
        </p>
      </div>
      <div className="flex shrink-0 items-center gap-2">
        {row.state === 'ACTIVE' ? (
          <>
            <button
              type="button"
              data-testid={`subscription-suspend-${row.key}`}
              onClick={() =>
                onOpen({ domainKey: row.key, label: row.label, kind: 'suspend' })
              }
              className="rounded-md border border-border px-3 py-1.5 text-sm font-medium text-foreground transition-colors hover:bg-muted"
            >
              일시중지
            </button>
            <button
              type="button"
              data-testid={`subscription-cancel-${row.key}`}
              onClick={() =>
                onOpen({ domainKey: row.key, label: row.label, kind: 'cancel' })
              }
              className="rounded-md border border-destructive/40 px-3 py-1.5 text-sm font-medium text-destructive transition-colors hover:bg-destructive/10"
            >
              해지
            </button>
          </>
        ) : (
          <button
            type="button"
            data-testid={`subscription-subscribe-${row.key}`}
            onClick={() =>
              onOpen({ domainKey: row.key, label: row.label, kind: 'subscribe' })
            }
            className="rounded-md bg-primary px-3 py-1.5 text-sm font-medium text-primary-foreground transition-colors hover:bg-primary/90"
          >
            구독
          </button>
        )}
      </div>
    </li>
  );
}
