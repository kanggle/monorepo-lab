'use client';

import { messageForCode } from '@/shared/api/errors';
import { formatDateTime } from '@/shared/lib/datetime';
import type { Inspection } from '../api/types';

/**
 * The ASN 검수(inspection) inline panel for {@link WmsInboundScreen}
 * (TASK-PC-FE-222 — `getAsnInspection`, mirrors `WmsInventoryDetailPanel`'s
 * composite-key by-key lookup pattern). The container owns the selection +
 * the `useWmsAsnInspection` query; this presentational panel renders the
 * derived branch: pre-select placeholder → loading → forbidden → 404 "검수
 * 내역 없음" (distinguished from a degrade — Edge Case: an ASN before
 * `inbound.inspection.completed` has no projected row yet) → degrade → the
 * aggregate field grid (the admin read-model projects per-ASN totals, not a
 * per-line breakdown). Pure presentation — markup + testids preserved
 * verbatim (`wms-asn-inspection-*`).
 */
export interface AsnInspectionPanelProps {
  /** `true` once a row's "검수" has been clicked (selected !== null). */
  selected: boolean;
  loading: boolean;
  forbidden: boolean;
  notFound: boolean;
  degraded: boolean;
  data: Inspection | undefined;
}

export function AsnInspectionPanel({
  selected,
  loading,
  forbidden,
  notFound,
  degraded,
  data,
}: AsnInspectionPanelProps) {
  return (
    <>
      {/* ── 검수 상세 (TASK-PC-FE-222, `getAsnInspection`) ──────────────── */}
      <h2 className="mb-3 text-lg font-medium text-foreground">검수 상세</h2>
      <div
        data-testid="wms-asn-inspection-panel"
        className="rounded-md border border-border p-4"
      >
        {!selected ? (
          <p
            className="text-sm text-muted-foreground"
            data-testid="wms-asn-inspection-empty"
          >
            행의 &quot;검수&quot; 버튼을 클릭하면 검수 결과를 확인할 수
            있습니다.
          </p>
        ) : loading ? (
          <p
            className="text-sm text-muted-foreground"
            data-testid="wms-asn-inspection-loading"
          >
            불러오는 중…
          </p>
        ) : forbidden ? (
          <div
            role="status"
            data-testid="wms-asn-inspection-forbidden"
            className="text-sm text-muted-foreground"
          >
            {messageForCode('FORBIDDEN')}
          </div>
        ) : notFound ? (
          <p
            className="text-sm text-muted-foreground"
            data-testid="wms-asn-inspection-notfound"
          >
            검수 내역 없음 (아직 검수가 완료되지 않았습니다).
          </p>
        ) : degraded || !data ? (
          <div
            role="status"
            data-testid="wms-asn-inspection-degraded"
            className="text-sm text-muted-foreground"
          >
            검수 상세를 일시적으로 불러올 수 없습니다. 잠시 후 다시
            시도하세요.
          </div>
        ) : (
          <dl className="grid grid-cols-2 gap-2 text-sm sm:grid-cols-4">
            <div>
              <dt className="text-muted-foreground">완료 시각</dt>
              <dd data-testid="wms-asn-inspection-completedat">
                {formatDateTime(data.inspectionCompletedAt)}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground">검수자</dt>
              <dd data-testid="wms-asn-inspection-inspector">
                {data.inspectorId ?? '—'}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground">총 라인</dt>
              <dd data-testid="wms-asn-inspection-totallines">
                {data.totalLines ?? '—'}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground">불일치 라인</dt>
              <dd data-testid="wms-asn-inspection-discrepancy">
                {data.discrepancyCount ?? '—'}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground">예정 수량</dt>
              <dd data-testid="wms-asn-inspection-expected">
                {data.totalQtyExpected ?? '—'}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground">합격 수량</dt>
              <dd data-testid="wms-asn-inspection-passed">
                {data.totalQtyPassed ?? '—'}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground">손상 수량</dt>
              <dd data-testid="wms-asn-inspection-damaged">
                {data.totalQtyDamaged ?? '—'}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground">부족 수량</dt>
              <dd data-testid="wms-asn-inspection-short">
                {data.totalQtyShort ?? '—'}
              </dd>
            </div>
          </dl>
        )}
      </div>
    </>
  );
}
