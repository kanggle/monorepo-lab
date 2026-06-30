'use client';

import type { SnapshotResponse, SnapshotRow } from '../api/types';
import { S5Warning } from './S5Warning';

/**
 * Inventory-visibility snapshot region of the scm ops screen
 * (TASK-PC-FE-144 split) — the REQUIRED S5 `meta.warning` (surfaced
 * prominently, never stripped) + the cross-node snapshot table (or the
 * empty notice). Pure presentation; the row list is normalised in the
 * container and arrives via props. STRICTLY READ-ONLY.
 */
export interface ScmSnapshotTableProps {
  warning: SnapshotResponse['meta']['warning'];
  rows: SnapshotRow[];
}

export function ScmSnapshotTable({ warning, rows }: ScmSnapshotTableProps) {
  return (
    <>
      {/* ── inventory-visibility: snapshot (S5 warning REQUIRED) ──────── */}
      <h2 className="mb-3 text-lg font-medium text-foreground">
        재고 가시성 — 스냅샷
      </h2>
      <S5Warning warning={warning} />
      {rows.length === 0 ? (
        <p
          className="mb-8 text-sm text-muted-foreground"
          data-testid="scm-snap-empty"
        >
          표시할 스냅샷이 없습니다.
        </p>
      ) : (
        <table
          className="mb-8 data-table"
          data-testid="scm-snap-table"
        >
          <caption className="sr-only">재고 가시성 스냅샷</caption>
          <thead>
            <tr className="border-b border-border text-left">
              <th scope="col" className="p-2">
                노드
              </th>
              <th scope="col" className="p-2">
                SKU
              </th>
              <th scope="col" className="p-2">
                수량
              </th>
              <th scope="col" className="p-2">
                신선도
              </th>
              <th scope="col" className="p-2">
                마지막 이벤트 (UTC)
              </th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r, i) => (
              <tr
                key={r.id ?? `${r.nodeId ?? 'n'}-${r.sku ?? 's'}-${i}`}
                data-testid={`scm-snap-row-${i}`}
                className="border-b border-border"
              >
                <td className="p-2">{r.nodeId ?? '—'}</td>
                <td className="p-2">{r.sku ?? '—'}</td>
                <td className="p-2">{r.quantity ?? '—'}</td>
                <td className="p-2">{r.staleness ?? '—'}</td>
                <td className="p-2">{r.lastEventAt ?? '—'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </>
  );
}
