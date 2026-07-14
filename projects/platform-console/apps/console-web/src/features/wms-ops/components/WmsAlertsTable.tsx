'use client';

import { Button } from '@/shared/ui/Button';
import { StatusBadge } from '@/shared/ui/StatusBadge';
import type { AlertRow } from '../api/types';

/**
 * Alerts region of the wms ops screen (TASK-PC-FE-103 split) — the alert table
 * with the per-row confirm-gated acknowledge affordance (the screen's only
 * mutation; the confirm dialog itself stays in the container). Pure
 * presentation: the rows + the ack-open callback arrive via props.
 */
export interface WmsAlertsTableProps {
  rows: AlertRow[];
  onAck: (alert: AlertRow) => void;
}

export function WmsAlertsTable({ rows, onAck }: WmsAlertsTableProps) {
  return (
    <>
      {/* ── Alerts (confirm-gated acknowledge — the only mutation) ─────── */}
      <h2 className="mb-3 text-lg font-medium text-foreground">알림</h2>
      {rows.length === 0 ? (
        <p
          className="text-sm text-muted-foreground"
          data-testid="wms-alerts-empty"
        >
          표시할 알림이 없습니다.
        </p>
      ) : (
        <table
          className="data-table"
          data-testid="wms-alerts-table"
        >
          <caption className="sr-only">알림 목록</caption>
          <thead>
            <tr className="border-b border-border text-left">
              <th scope="col" className="p-2">
                유형
              </th>
              <th scope="col" className="p-2">
                메시지
              </th>
              <th scope="col" className="p-2">
                감지 시각 (UTC)
              </th>
              <th scope="col" className="p-2">
                상태
              </th>
              <th scope="col" className="p-2">
                작업
              </th>
            </tr>
          </thead>
          <tbody>
            {rows.map((a, i) => (
              <tr
                key={a.alertId}
                data-testid={`wms-alert-row-${i}`}
                className="border-b border-border"
              >
                <td className="p-2">{a.alertType ?? '—'}</td>
                <td className="p-2">{a.message ?? '—'}</td>
                <td className="p-2">{a.detectedAt ?? '—'}</td>
                <td className="p-2">
                  {a.acknowledged ? (
                    <StatusBadge
                      tone="neutral"
                      data-testid={`wms-alert-acked-${i}`}
                    >
                      확인됨
                    </StatusBadge>
                  ) : (
                    <StatusBadge tone="warning">미확인</StatusBadge>
                  )}
                </td>
                <td className="p-2">
                  {a.acknowledged ? (
                    <span className="text-xs text-muted-foreground">—</span>
                  ) : (
                    <Button
                      variant="secondary"
                      size="sm"
                      onClick={() => onAck(a)}
                      data-testid={`wms-alert-ack-${i}`}
                    >
                      확인 처리
                    </Button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </>
  );
}
