'use client';

import { useId, useMemo, useState } from 'react';
import { ApiError } from '@/shared/api/errors';
import { useWmsShipments } from '../hooks/use-wms-ops';
import {
  WMS_DEFAULT_PAGE_SIZE,
  type ShipmentPage,
  type ShipmentQueryParams,
} from '../api/types';
import { WmsShipmentsTable } from './WmsShipmentsTable';
import { type ShipFilterState, EMPTY_SHIP_FILTERS } from './wms-ops-helpers';

/**
 * wms **택배/출고 (shipments)** read section — TASK-PC-FE-175, moved off the
 * `/wms` 개요 onto the existing `/wms/outbound` 출고 page (the filter +
 * pagination query table is unfit for a glance-overview, PC-FE-170/173 same
 * principle; the outbound page already exists, so no new route). Rendered as a
 * SECTION below `OutboundOpsScreen` (the outbound-order operations) — the
 * read-side companion (confirmed shipments: carrier / tracking / shipped-at)
 * to the write-side operations above it.
 *
 * This container owns the shipment filter/query/pagination state (moved
 * verbatim from `WmsOpsScreen`); `WmsShipmentsTable` stays the prop-driven
 * presentational child. Read-only — no mutation affordance.
 *
 * Resilience (§ 2.5, mirrors `WmsInventoryScreen`): 401 is handled by the
 * server route (whole-session re-login); a client re-query `403` → inline
 * forbidden; `503`/timeout → this section degrades only (the outbound screen +
 * console shell stay intact). The server-seed's own forbidden/degraded is
 * gated by the page BEFORE this screen renders.
 */
export interface WmsShipmentsScreenProps {
  /** Server-seeded page-0 shipments read (the table's initialData). */
  shipments: ShipmentPage;
  /** NON-blocking eventual-consistency hint (seconds), or null. */
  lagSeconds: number | null;
}

export function WmsShipmentsScreen({
  shipments,
  lagSeconds,
}: WmsShipmentsScreenProps) {
  const shipWhFid = useId();
  const shipCarrierFid = useId();

  const [shipFilters, setShipFilters] =
    useState<ShipFilterState>(EMPTY_SHIP_FILTERS);
  const [shipQuery, setShipQuery] = useState<ShipmentQueryParams>({
    page: 0,
    size: shipments.page.size || WMS_DEFAULT_PAGE_SIZE,
  });

  const shipSeeded =
    (shipQuery.page ?? 0) === 0 &&
    !shipQuery.warehouseId &&
    !shipQuery.carrierCode;

  const ship = useWmsShipments(shipQuery, shipSeeded ? shipments : undefined);
  const shipData = ship.data ?? shipments;

  const shipApiError =
    ship.error instanceof ApiError ? (ship.error as ApiError) : null;
  const shipForbidden = shipApiError?.status === 403;
  const shipDegraded =
    ship.isError &&
    (!shipApiError || shipApiError.status >= 500) &&
    !shipForbidden;

  function submitShipFilters(e: React.FormEvent) {
    e.preventDefault();
    setShipQuery({
      warehouseId: shipFilters.warehouseId.trim() || undefined,
      carrierCode: shipFilters.carrierCode.trim() || undefined,
      page: 0,
      size: shipments.page.size || WMS_DEFAULT_PAGE_SIZE,
    });
  }

  const lagBanner = useMemo(() => {
    if (lagSeconds === null || lagSeconds <= 0) return null;
    return `데이터가 약 ${Math.round(lagSeconds)}초 지연될 수 있습니다 (읽기 모델은 최종 일관성 — 표시값이 잠시 과거일 수 있습니다).`;
  }, [lagSeconds]);

  return (
    <section aria-label="택배 / 출고 조회" className="mt-10">
      {lagBanner && (
        <div
          role="status"
          data-testid="wms-ship-lag-hint"
          className="mb-6 rounded-md border border-amber-300/50 bg-amber-50 px-4 py-3 text-sm text-amber-900 dark:border-amber-700/40 dark:bg-amber-950/40 dark:text-amber-200"
        >
          {lagBanner}
        </div>
      )}

      <WmsShipmentsTable
        shipWhFid={shipWhFid}
        shipCarrierFid={shipCarrierFid}
        filters={shipFilters}
        onFiltersChange={setShipFilters}
        onSubmit={submitShipFilters}
        forbidden={shipForbidden}
        degraded={shipDegraded}
        data={shipData}
        query={shipQuery}
        onPrevPage={() =>
          setShipQuery((q) => ({ ...q, page: Math.max(0, (q.page ?? 0) - 1) }))
        }
        onNextPage={() =>
          setShipQuery((q) => ({ ...q, page: (q.page ?? 0) + 1 }))
        }
      />
    </section>
  );
}
