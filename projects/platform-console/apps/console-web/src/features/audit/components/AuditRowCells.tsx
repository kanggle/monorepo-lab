import type { AuditRow } from '../api/types';

/**
 * Renders one audit row's cells **discriminated by its `source`**
 * (console-integration-contract § 2.4.2). The unified table has a fixed
 * column grid; each source fills it with its own meaningful fields:
 *
 *   - `admin`        → actionCode / operatorId / targetId / outcome
 *   - `login_history`→ (login) / accountId / ipMasked·geo / outcome
 *   - `suspicious`   → (suspicious) / accountId / ipMasked·geo / outcome
 *   - anything else  → a GENERIC row (never throws — task Edge Case
 *                      "Unknown/future source value"; the producer may
 *                      add a source and the console must not crash).
 *
 * Producer-masked PII (ipMasked / geoCountry) is rendered as-is and never
 * un-masked, derived, or logged (§ 2.4.2 / audit-heavy A9).
 */

function Cell({
  children,
  testid,
  muted = false,
}: {
  children: React.ReactNode;
  testid?: string;
  muted?: boolean;
}) {
  return (
    <td
      className={`p-2 align-top ${muted ? 'text-muted-foreground' : ''}`}
      data-testid={testid}
    >
      {children ?? <span className="text-muted-foreground">—</span>}
    </td>
  );
}

export function AuditRowCells({ row }: { row: AuditRow }) {
  // The discriminant is always present (every schema branch requires it).
  const source = (row as { source: string }).source;

  if (source === 'admin') {
    const r = row as Extract<AuditRow, { source: 'admin' }>;
    return (
      <>
        <Cell testid="cell-source">
          <span className="rounded bg-muted px-1.5 py-0.5 text-xs font-medium">
            admin
          </span>
        </Cell>
        <Cell testid="cell-primary">{r.actionCode}</Cell>
        <Cell testid="cell-actor" muted>
          {r.operatorId}
        </Cell>
        <Cell testid="cell-target" muted>
          {r.targetId ?? null}
        </Cell>
        <Cell testid="cell-outcome">{r.outcome}</Cell>
        <Cell testid="cell-time" muted>
          {r.occurredAt}
        </Cell>
      </>
    );
  }

  if (source === 'login_history' || source === 'suspicious') {
    const r = row as Extract<
      AuditRow,
      { source: 'login_history' | 'suspicious' }
    >;
    const loc = [r.ipMasked, r.geoCountry].filter(Boolean).join(' · ');
    return (
      <>
        <Cell testid="cell-source">
          <span className="rounded bg-muted px-1.5 py-0.5 text-xs font-medium">
            {source === 'login_history' ? 'login' : 'suspicious'}
          </span>
        </Cell>
        <Cell testid="cell-primary" muted>
          {('eventId' in r ? r.eventId : null) ?? null}
        </Cell>
        <Cell testid="cell-actor" muted>
          {('accountId' in r ? r.accountId : null) ?? null}
        </Cell>
        <Cell testid="cell-target" muted>
          {loc || null}
        </Cell>
        <Cell testid="cell-outcome">
          {('outcome' in r ? r.outcome : null) ?? null}
        </Cell>
        <Cell testid="cell-time" muted>
          {r.occurredAt}
        </Cell>
      </>
    );
  }

  // Unknown / future source → generic, never crash. Show the raw
  // discriminant + timestamp; everything else collapses to a placeholder.
  const generic = row as { source: string; occurredAt?: string };
  return (
    <>
      <Cell testid="cell-source">
        <span className="rounded bg-muted px-1.5 py-0.5 text-xs font-medium">
          {generic.source}
        </span>
      </Cell>
      <Cell testid="cell-primary" muted>
        <span data-testid="generic-row-note">
          (지원되지 않는 항목 유형)
        </span>
      </Cell>
      <Cell testid="cell-actor" muted>
        {null}
      </Cell>
      <Cell testid="cell-target" muted>
        {null}
      </Cell>
      <Cell testid="cell-outcome" muted>
        {null}
      </Cell>
      <Cell testid="cell-time" muted>
        {generic.occurredAt ?? null}
      </Cell>
    </>
  );
}

/** Stable per-row React key — known rows have an id, generic rows fall
 *  back to source+time (no PII used as a key). */
export function auditRowKey(row: AuditRow, index: number): string {
  const r = row as Record<string, unknown>;
  if (typeof r.auditId === 'string') return `a:${r.auditId}`;
  if (typeof r.eventId === 'string') return `e:${r.eventId}`;
  const src = typeof r.source === 'string' ? r.source : 'unknown';
  const at = typeof r.occurredAt === 'string' ? r.occurredAt : '';
  return `g:${src}:${at}:${index}`;
}
