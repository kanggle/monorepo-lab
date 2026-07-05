'use client';

import { messageForCode } from '@/shared/api/errors';
import { formatDateTime } from '@/shared/lib/datetime';
import type { InventoryRow } from '../api/types';

/**
 * The 재고 상세 (composite-key by-key lookup) panel for {@link WmsInventoryScreen}
 * (TASK-PC-FE-197 split, TASK-PC-FE-173 feature). The container owns the
 * selection + the `getInventoryByKey` query; this presentational panel renders
 * the derived branch: pre-select placeholder → loading → forbidden → 404
 * "재고 없음" (distinguished from a degrade) → degrade → the field grid. Pure
 * presentation — markup + testids preserved verbatim (`wms-inv-detail-*`).
 */
export interface WmsInventoryDetailPanelProps {
  /** `true` once a row's "상세" has been clicked (selected !== null). */
  selected: boolean;
  loading: boolean;
  forbidden: boolean;
  notFound: boolean;
  degraded: boolean;
  data: InventoryRow | undefined;
}

export function WmsInventoryDetailPanel({
  selected,
  loading,
  forbidden,
  notFound,
  degraded,
  data,
}: WmsInventoryDetailPanelProps) {
  return (
    <>
      {/* ── Row detail panel (TASK-PC-FE-173, `getInventoryByKey`) ──────── */}
      <h2 className="mb-3 text-lg font-medium text-foreground">재고 상세</h2>
      <div
        data-testid="wms-inv-detail-panel"
        className="rounded-md border border-border p-4"
      >
        {!selected ? (
          <p
            className="text-sm text-muted-foreground"
            data-testid="wms-inv-detail-empty"
          >
            행의 &quot;상세&quot; 버튼을 클릭하면 재고 상세를 확인할 수
            있습니다.
          </p>
        ) : loading ? (
          <p
            className="text-sm text-muted-foreground"
            data-testid="wms-inv-detail-loading"
          >
            불러오는 중…
          </p>
        ) : forbidden ? (
          <div
            role="status"
            data-testid="wms-inv-detail-forbidden"
            className="text-sm text-muted-foreground"
          >
            {messageForCode('FORBIDDEN')}
          </div>
        ) : notFound ? (
          <p
            className="text-sm text-muted-foreground"
            data-testid="wms-inv-detail-notfound"
          >
            재고 없음 (해당 위치·SKU·로트 조합의 재고 수량이 0입니다).
          </p>
        ) : degraded || !data ? (
          <div
            role="status"
            data-testid="wms-inv-detail-degraded"
            className="text-sm text-muted-foreground"
          >
            재고 상세를 일시적으로 불러올 수 없습니다. 잠시 후 다시
            시도하세요.
          </div>
        ) : (
          <dl className="grid grid-cols-2 gap-2 text-sm sm:grid-cols-4">
            <div>
              <dt className="text-muted-foreground">위치</dt>
              <dd data-testid="wms-inv-detail-location">
                {data.locationCode ?? data.locationId}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground">SKU</dt>
              <dd data-testid="wms-inv-detail-sku">
                {data.skuCode ?? data.skuId}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground">로트</dt>
              <dd data-testid="wms-inv-detail-lot">
                {data.lotNo ?? data.lotId ?? '—'}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground">창고</dt>
              <dd data-testid="wms-inv-detail-warehouse">
                {data.warehouseId}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground">가용</dt>
              <dd data-testid="wms-inv-detail-available">
                {data.availableQty ?? '—'}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground">예약</dt>
              <dd data-testid="wms-inv-detail-reserved">
                {data.reservedQty ?? '—'}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground">손상</dt>
              <dd data-testid="wms-inv-detail-damaged">
                {data.damagedQty ?? '—'}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground">보유</dt>
              <dd data-testid="wms-inv-detail-onhand">
                {data.onHandQty ?? '—'}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground">최근 조정</dt>
              <dd data-testid="wms-inv-detail-lastadjusted">
                {formatDateTime(data.lastAdjustedAt)}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground">최근 이벤트</dt>
              <dd data-testid="wms-inv-detail-lastevent">
                {formatDateTime(data.lastEventAt)}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground">version</dt>
              <dd data-testid="wms-inv-detail-version">
                {data.version ?? '—'}
              </dd>
            </div>
          </dl>
        )}
      </div>
    </>
  );
}
