'use client';

import { Button } from '@/shared/ui/Button';
import { StatusBadge } from '@/shared/ui/StatusBadge';
import type { AlertRow } from '../api/types';
import { alertSummary, isAlertAcknowledged } from './wms-ops-helpers';

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
  /** TASK-PC-FE-373 — rows the page parser dropped (unparseable); surfaced, never silent. */
  excludedRows?: number;
}

export function WmsAlertsTable({
  rows,
  onAck,
  canAcknowledge = true,
  excludedRows = 0,
}: WmsAlertsTableProps) {
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
      {excludedRows > 0 && (
        <p
          role="note"
          data-testid="wms-alerts-excluded-note"
          className="mb-2 text-xs text-muted-foreground"
        >
          해석할 수 없는 알림 {excludedRows}건은 표에서 제외했습니다.
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
                내용
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
                key={a.id}
                data-testid={`wms-alert-row-${i}`}
                className="border-b border-border"
              >
                <td className="p-2">{a.alertType ?? '—'}</td>
                <td className="p-2">{alertSummary(a)}</td>
                <td className="p-2">{a.detectedAt ?? '—'}</td>
                <td className="p-2">
                  {isAlertAcknowledged(a) ? (
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
                  {isAlertAcknowledged(a) || !canAcknowledge ? (
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
