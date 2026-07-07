import Link from 'next/link';
import { cn } from '@/shared/lib/cn';
import { formatDateTime } from '@/shared/lib/datetime';
import type { AuditRow } from '@/features/audit/api/types';
import type { CellStatus } from '../api/overview-state';
import { AUDIT_SOURCE_LABEL, STATUS_DOT, STATUS_LABEL } from './overview-labels';
import { Placeholder } from './IamOverviewPrimitives';

/**
 * IAM overview recent audit·security mini-list card (TASK-PC-FE-180 — extracted
 * from {@link IamOverviewScreen}, TASK-PC-FE-212 presentational split). Server
 * component — STRICTLY READ-ONLY, no `'use client'`. Markup / testids are
 * byte-verbatim from the former god-file.
 */

/**
 * Row display shape — tolerant of the AuditRow discriminated union (+ the
 * generic `.passthrough()` fallback whose broad `source: string` defeats clean
 * discriminated narrowing), so a producer evolution never crashes the mini-list.
 * Read fields off one permissive record view instead of per-variant narrowing.
 */
export function auditRowView(
  row: AuditRow,
  index: number,
): { key: string; source: string; primary: string; occurredAt?: string } {
  const r = row as {
    source: string;
    auditId?: string;
    eventId?: string;
    actionCode?: string;
    outcome?: string | null;
    occurredAt?: string;
  };
  const source = AUDIT_SOURCE_LABEL[r.source] ?? r.source;
  const key = r.auditId ?? r.eventId ?? `row-${index}`;
  let primary: string;
  if (r.source === 'admin') {
    primary = r.actionCode ?? source;
  } else if (r.source === 'login_history' || r.source === 'suspicious') {
    primary = r.outcome ?? source;
  } else {
    primary = source;
  }
  return { key, source, primary, occurredAt: r.occurredAt };
}

export function AuditCard({
  status,
  total,
  recent,
}: {
  status: CellStatus;
  total: number | null;
  recent: AuditRow[] | null;
}) {
  const ok = status === 'ok';
  return (
    <div
      data-testid="iam-overview-audit"
      data-status={status}
      className="flex flex-col gap-3 rounded-md border border-border bg-background px-4 py-4"
    >
      <Link
        href="/audit"
        data-testid="iam-overview-audit-link"
        className="flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
      >
        <span
          className={cn('h-1.5 w-1.5 shrink-0 rounded-full', STATUS_DOT[status])}
          aria-hidden="true"
        />
        감사 · 보안
        <span className="sr-only">상태: {STATUS_LABEL[status]}</span>
      </Link>
      {!ok ? (
        <Placeholder status={status} testid="iam-overview-audit-degraded" />
      ) : (
        <>
          <span className="text-xs text-muted-foreground">
            스코프 내 총 이벤트{' '}
            <span
              className="font-medium tabular-nums text-foreground"
              data-testid="iam-overview-audit-total"
            >
              {(total ?? 0).toLocaleString()}
            </span>
          </span>
          {!recent || recent.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              최근 이벤트가 없습니다.
            </p>
          ) : (
            <ul className="space-y-2 text-sm" data-testid="iam-overview-audit-recent">
              {recent.map((row, i) => {
                const v = auditRowView(row, i);
                return (
                  <li
                    key={v.key}
                    className="flex items-center justify-between gap-3 border-b border-border pb-2 last:border-0 last:pb-0"
                  >
                    <span className="shrink-0 rounded bg-muted px-1.5 py-0.5 text-[11px] text-muted-foreground">
                      {v.source}
                    </span>
                    <span className="min-w-0 flex-1 truncate text-foreground">
                      {v.primary}
                    </span>
                    <span className="hidden shrink-0 text-xs text-muted-foreground sm:inline">
                      {formatDateTime(v.occurredAt)}
                    </span>
                  </li>
                );
              })}
            </ul>
          )}
        </>
      )}
    </div>
  );
}
