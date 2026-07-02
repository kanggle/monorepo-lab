'use client';

import type { UseQueryResult } from '@tanstack/react-query';
import { Button } from '@/shared/ui/Button';
import type {
  DelegationGrant,
  DelegationListResponse,
} from '../api/delegation-types';
import { isActiveGrant } from '../api/delegation-types';
import { approvalErrorMessage } from './approval-error';
import { formatDateTime } from '@/shared/lib/datetime';

/**
 * Presentational delegation grant list (TASK-PC-FE-150 — behaviour-
 * preserving extraction of the two near-identical list blocks formerly
 * inlined in `DelegationScreen`). Renders one grant list with its heading,
 * query loading / empty / error states, the per-row status badge + period,
 * and — for the DELEGATOR list only — a per-row revoke action. The
 * DELEGATE list passes no `onRevoke` (revoke is delegator-only).
 */

// ---------------------------------------------------------------------------
// Status badge.
// ---------------------------------------------------------------------------

export function DelegationStatusBadge({ grant }: { grant: DelegationGrant }) {
  if (grant.status === 'REVOKED') {
    return (
      <span
        data-testid="delegation-status-badge"
        data-status="REVOKED"
        className="inline-block rounded px-1.5 py-0.5 text-xs bg-red-100 text-red-800 dark:bg-red-950/60 dark:text-red-100"
      >
        회수됨
      </span>
    );
  }
  // ACTIVE — check if expired
  const active = isActiveGrant(grant);
  if (!active) {
    return (
      <span
        data-testid="delegation-status-badge"
        data-status="ACTIVE_EXPIRED"
        className="inline-block rounded px-1.5 py-0.5 text-xs bg-muted text-muted-foreground"
      >
        만료
      </span>
    );
  }
  return (
    <span
      data-testid="delegation-status-badge"
      data-status="ACTIVE"
      className="inline-block rounded px-1.5 py-0.5 text-xs bg-green-100 text-green-800 dark:bg-green-950/60 dark:text-green-100"
    >
      활성
    </span>
  );
}

// ---------------------------------------------------------------------------
// Period display helper.
// ---------------------------------------------------------------------------

function fmt(ts: string | undefined): string {
  return formatDateTime(ts, '—');
}

function periodText(grant: DelegationGrant): string {
  const from = fmt(grant.validFrom);
  const to = grant.validTo ? fmt(grant.validTo) : '무기한';
  return `${from} ~ ${to}`;
}

// ---------------------------------------------------------------------------
// Grant list.
// ---------------------------------------------------------------------------

export interface DelegationGrantListProps {
  /** Wrapper `data-testid` — `delegation-list-delegator` / `-delegate`. */
  testid: string;
  heading: string;
  query: UseQueryResult<DelegationListResponse>;
  grants: DelegationGrant[];
  emptyText: string;
  /** Which counterparty id to show — DELEGATOR list shows the delegate,
   *  DELEGATE list shows the delegator. */
  idField: 'delegateId' | 'delegatorId';
  /** Per-row revoke handler — present ONLY on the DELEGATOR list (revoke
   *  is delegator-only); absent → no revoke action rendered. */
  onRevoke?: (grant: DelegationGrant) => void;
}

export function DelegationGrantList({
  testid,
  heading,
  query,
  grants,
  emptyText,
  idField,
  onRevoke,
}: DelegationGrantListProps) {
  return (
    <div className="mb-6" data-testid={testid}>
      <h3 className="mb-2 text-sm font-semibold text-foreground">{heading}</h3>
      {query.isError && (
        <p
          className="mb-2 text-sm text-destructive"
          role="status"
          data-testid="delegation-error"
        >
          {approvalErrorMessage(query.error)}
        </p>
      )}
      {query.isLoading ? (
        <p className="text-sm text-muted-foreground">불러오는 중…</p>
      ) : grants.length === 0 ? (
        <p className="text-sm text-muted-foreground">{emptyText}</p>
      ) : (
        <ul className="space-y-1">
          {grants.map((g: DelegationGrant) => (
            <li
              key={g.id}
              data-testid={`delegation-row-${g.id}`}
              className="flex items-center justify-between rounded border border-border px-3 py-2 text-sm"
            >
              <div className="flex-1 min-w-0">
                <span className="font-medium">{g[idField]}</span>
                <span className="ml-2 text-xs text-muted-foreground">
                  {periodText(g)}
                </span>
              </div>
              {onRevoke ? (
                <div className="ml-2 flex items-center gap-2">
                  <DelegationStatusBadge grant={g} />
                  {/* Revoke action only on ACTIVE (not expired) delegator grants */}
                  {isActiveGrant(g) && (
                    <Button
                      variant="secondary"
                      onClick={() => onRevoke(g)}
                      data-testid={`delegation-revoke-${g.id}`}
                      className="text-xs text-destructive"
                    >
                      회수
                    </Button>
                  )}
                </div>
              ) : (
                <div className="ml-2">
                  <DelegationStatusBadge grant={g} />
                  {/* NO revoke action — revoke is delegator-only */}
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
