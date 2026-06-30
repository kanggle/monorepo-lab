'use client';

import type { StalenessResponse } from '../api/types';
import { S5Warning } from './S5Warning';

/**
 * Inventory-visibility node staleness panel of the scm ops screen
 * (TASK-PC-FE-144 split) — the REQUIRED S5 `meta.warning` (surfaced, never
 * stripped) + the per-node staleness table. STALE / UNREACHABLE nodes are
 * shown honestly, never hidden (§ 2.4.6 freshness honesty). Pure
 * presentation; the rows arrive via props. STRICTLY READ-ONLY.
 */
export interface ScmStalenessTableProps {
  warning: StalenessResponse['meta']['warning'];
  rows: StalenessResponse['data'];
}

export function ScmStalenessTable({ warning, rows }: ScmStalenessTableProps) {
  return (
    <>
      {/* ── inventory-visibility: node staleness panel (S5 + honest) ──── */}
      <h2 className="mb-3 text-lg font-medium text-foreground">
        재고 가시성 — 노드 신선도
      </h2>
      <S5Warning warning={warning} />
      {rows.length === 0 ? (
        <p
          className="text-sm text-muted-foreground"
          data-testid="scm-staleness-empty"
        >
          표시할 노드가 없습니다.
        </p>
      ) : (
        <table
          className="data-table"
          data-testid="scm-staleness-table"
        >
          <caption className="sr-only">노드 신선도</caption>
          <thead>
            <tr className="border-b border-border text-left">
              <th scope="col" className="p-2">
                노드
              </th>
              <th scope="col" className="p-2">
                상태
              </th>
              <th scope="col" className="p-2">
                마지막 이벤트 (UTC)
              </th>
              <th scope="col" className="p-2">
                마지막 점검 (UTC)
              </th>
            </tr>
          </thead>
          <tbody>
            {rows.map((s, i) => {
              const status = s.stalenessStatus ?? 'UNKNOWN';
              const bad = status === 'STALE' || status === 'UNREACHABLE';
              return (
                <tr
                  key={`${s.nodeId}-${i}`}
                  data-testid={`scm-staleness-row-${i}`}
                  className="border-b border-border"
                >
                  <td className="p-2">{s.nodeId}</td>
                  <td className="p-2">
                    {/* Honest: a STALE / UNREACHABLE node is shown as
                        such, never hidden (§ 2.4.6 freshness honesty). */}
                    <span
                      data-testid={`scm-staleness-status-${i}`}
                      className={
                        bad
                          ? 'rounded bg-destructive/15 px-1.5 py-0.5 text-xs text-destructive'
                          : 'rounded bg-muted px-1.5 py-0.5 text-xs text-muted-foreground'
                      }
                    >
                      {status}
                    </span>
                  </td>
                  <td className="p-2">{s.lastEventAt ?? '—'}</td>
                  <td className="p-2">{s.lastCheckedAt ?? '—'}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}
    </>
  );
}
