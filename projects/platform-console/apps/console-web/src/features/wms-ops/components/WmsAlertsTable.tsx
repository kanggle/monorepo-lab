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
  /**
   * TASK-PC-FE-338 c-2 AC-2 — `false` hides «확인 처리» (the operator holds no `WMS_OPERATOR`,
   * which the server's acknowledge requires). Default `true` (fail-open).
   */
  canAcknowledge?: boolean;
}

export function WmsAlertsTable({ rows, onAck, canAcknowledge = true }: WmsAlertsTableProps) {
  return (
    <>
      {/* ── Alerts (confirm-gated acknowledge — the only mutation) ─────── */}
      <h2 className="mb-3 text-lg font-medium text-foreground">알림</h2>
      {!canAcknowledge && (
        <p
          role="note"
          data-testid="wms-alerts-read-only-note"
          className="mb-2 text-xs text-muted-foreground"
        >
          조회 전용 — 알림 확인 처리에 필요한 업무 권한(WMS 운영 조치)이 이 테넌트에서 배정되지
          않아 «확인 처리» 버튼을 숨겼습니다.
        </p>
      )}
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
                  {a.acknowledged || !canAcknowledge ? (
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
