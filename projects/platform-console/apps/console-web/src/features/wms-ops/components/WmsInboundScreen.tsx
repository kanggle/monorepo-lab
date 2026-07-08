'use client';

import { useId, useMemo, useState } from 'react';
import { ApiError } from '@/shared/api/errors';
import { useWmsAsns, useWmsAsnInspection } from '../hooks/use-wms-ops';
import {
  WMS_DEFAULT_PAGE_SIZE,
  type AsnPage,
  type AsnQueryParams,
  type AsnRow,
} from '../api/types';
import { WmsAsnTable } from './WmsAsnTable';
import { AsnInspectionPanel } from './AsnInspectionPanel';
import { WmsLagHint } from './WmsLagHint';
import { type InboundFilterState, EMPTY_INBOUND_FILTERS } from './wms-ops-helpers';

/**
 * wms **입고** (inbound / ASN) section — TASK-PC-FE-222, the console's first
 * dedicated surface for the "front half" of the inbound flow (창고 운영의
 * 앞단): ASN (입고예정) list + per-ASN 검수(inspection) inline detail.
 * Mirrors `WmsInventoryScreen` (TASK-PC-FE-173): this container owns the ASN
 * filter/query state, the pagination handlers, and the row-level "검수"
 * detail-panel state; `WmsAsnTable` stays the prop-driven presentational
 * child.
 *
 * Surfaces a previously-uncoded-but-unused producer capability: the console
 * API client (`wms-shipments-api.ts`) already exported `listAsns` +
 * `getAsnInspection` with zero consumers — this screen is the first.
 *
 * READ-ONLY (Out of Scope, task § Out of Scope): putaway instruct/confirm,
 * ASN creation, and inspection confirmation are raw `inbound-service` write
 * ops NOT projected onto the admin read-model this feature consumes.
 *
 * Resilience (§ 2.5, mirrors `WmsInventoryScreen`): 401 is handled by the
 * server route (whole-session re-login); 403 → inline forbidden; 503/timeout
 * → this section degrades only (the console shell stays intact); the
 * inspection lookup's `404` (no inspection projected yet) is distinguished
 * from a degrade — rendered as "검수 내역 없음", NOT an error state.
 */
export interface WmsInboundScreenProps {
  asns: AsnPage;
  /** NON-blocking eventual-consistency hint (seconds), or null. */
  lagSeconds: number | null;
}

export function WmsInboundScreen({ asns, lagSeconds }: WmsInboundScreenProps) {
  const statusFid = useId();
  const whFid = useId();
  const supplierFid = useId();
  const dateFromFid = useId();
  const dateToFid = useId();

  const [filters, setFilters] =
    useState<InboundFilterState>(EMPTY_INBOUND_FILTERS);
  const [query, setQuery] = useState<AsnQueryParams>({
    page: 0,
    size: asns.page.size || WMS_DEFAULT_PAGE_SIZE,
  });

  const seeded =
    (query.page ?? 0) === 0 &&
    !query.warehouseId &&
    !query.supplierPartnerId &&
    !query.status &&
    !query.expectedArriveDateFrom &&
    !query.expectedArriveDateTo;

  const asnQuery = useWmsAsns(query, seeded ? asns : undefined);
  const asnData = asnQuery.data ?? asns;

  const asnApiError =
    asnQuery.error instanceof ApiError ? (asnQuery.error as ApiError) : null;
  const forbidden = asnApiError?.status === 403;
  const degraded =
    asnQuery.isError && (!asnApiError || asnApiError.status >= 500) && !forbidden;

  function submitFilters(e: React.FormEvent) {
    e.preventDefault();
    setQuery({
      status: filters.status || undefined,
      warehouseId: filters.warehouseId.trim() || undefined,
      supplierPartnerId: filters.supplierPartnerId.trim() || undefined,
      expectedArriveDateFrom: filters.expectedArriveDateFrom || undefined,
      expectedArriveDateTo: filters.expectedArriveDateTo || undefined,
      page: 0,
      size: asns.page.size || WMS_DEFAULT_PAGE_SIZE,
    });
  }

  // ── 검수 상세 (per-ASN inspection lookup) ──────────────────────────────
  const [selectedAsnId, setSelectedAsnId] = useState<string | null>(null);
  const inspection = useWmsAsnInspection(selectedAsnId);

  function onInspect(row: AsnRow) {
    setSelectedAsnId(row.asnId);
  }

  const inspectionApiError =
    inspection.error instanceof ApiError
      ? (inspection.error as ApiError)
      : null;
  const inspectionNotFound = inspectionApiError?.status === 404;
  const inspectionForbidden = inspectionApiError?.status === 403;
  const inspectionDegraded =
    inspection.isError &&
    !inspectionNotFound &&
    !inspectionForbidden &&
    (!inspectionApiError || inspectionApiError.status >= 500);

  const lagBanner = useMemo(() => {
    if (lagSeconds === null || lagSeconds <= 0) return null;
    return `데이터가 약 ${Math.round(lagSeconds)}초 지연될 수 있습니다 (읽기 모델은 최종 일관성 — 표시값이 잠시 과거일 수 있습니다).`;
  }, [lagSeconds]);

  return (
    <section aria-labelledby="wms-inbound-heading">
      <h1 id="wms-inbound-heading" className="mb-2 text-2xl font-semibold">
        WMS 입고
      </h1>
      <p className="mb-6 text-sm text-muted-foreground">
        입고예정(ASN) 조회 (필터 · 페이징 · 검수 상세).
      </p>

      <WmsLagHint testid="wms-lag-hint" message={lagBanner} />

      <WmsAsnTable
        statusFid={statusFid}
        whFid={whFid}
        supplierFid={supplierFid}
        dateFromFid={dateFromFid}
        dateToFid={dateToFid}
        filters={filters}
        onFiltersChange={setFilters}
        onSubmit={submitFilters}
        forbidden={forbidden}
        degraded={degraded}
        data={asnData}
        query={query}
        onPrevPage={() =>
          setQuery((q) => ({ ...q, page: Math.max(0, (q.page ?? 0) - 1) }))
        }
        onNextPage={() => setQuery((q) => ({ ...q, page: (q.page ?? 0) + 1 }))}
        onInspect={onInspect}
      />

      <AsnInspectionPanel
        selected={selectedAsnId !== null}
        loading={inspection.isLoading}
        forbidden={inspectionForbidden}
        notFound={inspectionNotFound}
        degraded={inspectionDegraded}
        data={inspection.data}
      />
    </section>
  );
}
