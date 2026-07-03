'use client';

import { useId, useMemo, useState } from 'react';
import { ApiError, messageForCode } from '@/shared/api/errors';
import { formatDateTime } from '@/shared/lib/datetime';
import { useWmsInventory, useWmsInventoryByKey } from '../hooks/use-wms-ops';
import type { InventoryByKey } from '../hooks/use-wms-ops';
import {
  WMS_DEFAULT_PAGE_SIZE,
  type InventoryPage,
  type InventoryQueryParams,
  type InventoryRow,
} from '../api/types';
import { WmsInventoryTable } from './WmsInventoryTable';
import { type InvFilterState, EMPTY_INV_FILTERS } from './wms-ops-helpers';

/**
 * wms **재고** (inventory) section — TASK-PC-FE-173, split off the `/wms`
 * 개요 (the query table — filters + pagination — is unfit for a
 * glance-overview, PC-FE-170 same principle). This container owns the
 * (now-extended) inventory filter/query state, the pagination handlers, and
 * the row-level "상세" detail-panel state; `WmsInventoryTable` stays the
 * prop-driven presentational child.
 *
 * Surfaces two previously-uncoded-but-unused producer capabilities:
 *   - extra filters (`locationId`, `lotId`, `minOnHand` — the proxy/params
 *     already supported them; only the form fields were missing);
 *   - a per-row `getInventoryByKey` composite-key ("상세") detail lookup
 *     (inline panel, NOT a route — the key is composite location+sku+lot,
 *     not a single `[id]`).
 *
 * Resilience (§ 2.5, mirrors `WmsOpsScreen`): 401 is handled by the server
 * route (whole-session re-login); 403 → inline forbidden; 503/timeout → this
 * section degrades only (the console shell stays intact); the by-key detail
 * lookup's `404` (zero stock at that key) is distinguished from a degrade —
 * rendered as "재고 없음", NOT an error state.
 */
export interface WmsInventoryScreenProps {
  inventory: InventoryPage;
  /** NON-blocking eventual-consistency hint (seconds), or null. */
  lagSeconds: number | null;
}

export function WmsInventoryScreen({
  inventory,
  lagSeconds,
}: WmsInventoryScreenProps) {
  const whFid = useId();
  const skuFid = useId();
  const lowFid = useId();
  const locFid = useId();
  const lotFid = useId();
  const minFid = useId();

  const [invFilters, setInvFilters] =
    useState<InvFilterState>(EMPTY_INV_FILTERS);
  const [invQuery, setInvQuery] = useState<InventoryQueryParams>({
    page: 0,
    size: inventory.page.size || WMS_DEFAULT_PAGE_SIZE,
  });

  const invSeeded =
    (invQuery.page ?? 0) === 0 &&
    !invQuery.warehouseId &&
    !invQuery.skuId &&
    !invQuery.lotId &&
    !invQuery.locationId &&
    !invQuery.lowStockOnly &&
    invQuery.minOnHand === undefined;

  const inv = useWmsInventory(invQuery, invSeeded ? inventory : undefined);
  const invData = inv.data ?? inventory;

  const invApiError =
    inv.error instanceof ApiError ? (inv.error as ApiError) : null;
  const invForbidden = invApiError?.status === 403;
  const invDegraded =
    inv.isError && (!invApiError || invApiError.status >= 500) && !invForbidden;

  function submitInvFilters(e: React.FormEvent) {
    e.preventDefault();
    const trimmedMin = invFilters.minOnHand.trim();
    const parsedMin = trimmedMin === '' ? undefined : Number(trimmedMin);
    const minOnHand =
      parsedMin === undefined || Number.isNaN(parsedMin) ? undefined : parsedMin;
    setInvQuery({
      warehouseId: invFilters.warehouseId.trim() || undefined,
      skuId: invFilters.skuId.trim() || undefined,
      locationId: invFilters.locationId.trim() || undefined,
      lotId: invFilters.lotId.trim() || undefined,
      lowStockOnly: invFilters.lowStockOnly || undefined,
      minOnHand,
      page: 0,
      size: inventory.page.size || WMS_DEFAULT_PAGE_SIZE,
    });
  }

  // ── Row detail (composite-key by-key lookup) ──────────────────────────
  const [selected, setSelected] = useState<InventoryByKey | null>(null);
  const detail = useWmsInventoryByKey(selected);

  function onSelect(row: InventoryRow) {
    setSelected({
      locationId: row.locationId,
      skuId: row.skuId,
      lotId: row.lotId ?? undefined,
    });
  }

  const detailApiError =
    detail.error instanceof ApiError ? (detail.error as ApiError) : null;
  const detailNotFound = detailApiError?.status === 404;
  const detailForbidden = detailApiError?.status === 403;
  const detailDegraded =
    detail.isError &&
    !detailNotFound &&
    !detailForbidden &&
    (!detailApiError || detailApiError.status >= 500);

  const lagBanner = useMemo(() => {
    if (lagSeconds === null || lagSeconds <= 0) return null;
    return `데이터가 약 ${Math.round(lagSeconds)}초 지연될 수 있습니다 (읽기 모델은 최종 일관성 — 표시값이 잠시 과거일 수 있습니다).`;
  }, [lagSeconds]);

  return (
    <section aria-labelledby="wms-inventory-heading">
      <h1
        id="wms-inventory-heading"
        className="mb-2 text-2xl font-semibold"
      >
        WMS 재고
      </h1>
      <p className="mb-6 text-sm text-muted-foreground">
        재고 조회 (필터 · 페이징 · 상세).
      </p>

      {lagBanner && (
        <div
          role="status"
          data-testid="wms-lag-hint"
          className="mb-6 rounded-md border border-amber-300/50 bg-amber-50 px-4 py-3 text-sm text-amber-900 dark:border-amber-700/40 dark:bg-amber-950/40 dark:text-amber-200"
        >
          {lagBanner}
        </div>
      )}

      <WmsInventoryTable
        whFid={whFid}
        skuFid={skuFid}
        lowFid={lowFid}
        locFid={locFid}
        lotFid={lotFid}
        minFid={minFid}
        filters={invFilters}
        onFiltersChange={setInvFilters}
        onSubmit={submitInvFilters}
        forbidden={invForbidden}
        degraded={invDegraded}
        data={invData}
        query={invQuery}
        onPrevPage={() =>
          setInvQuery((q) => ({ ...q, page: Math.max(0, (q.page ?? 0) - 1) }))
        }
        onNextPage={() =>
          setInvQuery((q) => ({ ...q, page: (q.page ?? 0) + 1 }))
        }
        onSelect={onSelect}
      />

      {/* ── Row detail panel (TASK-PC-FE-173, `getInventoryByKey`) ──────── */}
      <h2 className="mb-3 text-lg font-medium text-foreground">재고 상세</h2>
      <div
        data-testid="wms-inv-detail-panel"
        className="rounded-md border border-border p-4"
      >
        {selected === null ? (
          <p
            className="text-sm text-muted-foreground"
            data-testid="wms-inv-detail-empty"
          >
            행의 &quot;상세&quot; 버튼을 클릭하면 재고 상세를 확인할 수
            있습니다.
          </p>
        ) : detail.isLoading ? (
          <p
            className="text-sm text-muted-foreground"
            data-testid="wms-inv-detail-loading"
          >
            불러오는 중…
          </p>
        ) : detailForbidden ? (
          <div
            role="status"
            data-testid="wms-inv-detail-forbidden"
            className="text-sm text-muted-foreground"
          >
            {messageForCode('FORBIDDEN')}
          </div>
        ) : detailNotFound ? (
          <p
            className="text-sm text-muted-foreground"
            data-testid="wms-inv-detail-notfound"
          >
            재고 없음 (해당 위치·SKU·로트 조합의 재고 수량이 0입니다).
          </p>
        ) : detailDegraded || !detail.data ? (
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
                {detail.data.locationCode ?? detail.data.locationId}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground">SKU</dt>
              <dd data-testid="wms-inv-detail-sku">
                {detail.data.skuCode ?? detail.data.skuId}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground">로트</dt>
              <dd data-testid="wms-inv-detail-lot">
                {detail.data.lotNo ?? detail.data.lotId ?? '—'}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground">창고</dt>
              <dd data-testid="wms-inv-detail-warehouse">
                {detail.data.warehouseId}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground">가용</dt>
              <dd data-testid="wms-inv-detail-available">
                {detail.data.availableQty ?? '—'}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground">예약</dt>
              <dd data-testid="wms-inv-detail-reserved">
                {detail.data.reservedQty ?? '—'}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground">손상</dt>
              <dd data-testid="wms-inv-detail-damaged">
                {detail.data.damagedQty ?? '—'}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground">보유</dt>
              <dd data-testid="wms-inv-detail-onhand">
                {detail.data.onHandQty ?? '—'}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground">최근 조정</dt>
              <dd data-testid="wms-inv-detail-lastadjusted">
                {formatDateTime(detail.data.lastAdjustedAt)}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground">최근 이벤트</dt>
              <dd data-testid="wms-inv-detail-lastevent">
                {formatDateTime(detail.data.lastEventAt)}
              </dd>
            </div>
            <div>
              <dt className="text-muted-foreground">version</dt>
              <dd data-testid="wms-inv-detail-version">
                {detail.data.version ?? '—'}
              </dd>
            </div>
          </dl>
        )}
      </div>
    </section>
  );
}
