'use client';

import { useId, useMemo, useState } from 'react';
import { ApiError } from '@/shared/api/errors';
import { useScmPoList, useScmSkuBreakdown } from '../hooks/use-scm-ops';
import {
  SCM_DEFAULT_PAGE_SIZE,
  type PoPage,
  type PurchaseOrder,
  type PoQueryParams,
  type SnapshotResponse,
  type StalenessResponse,
} from '../api/types';
import { PoDetailDialog } from './PoDetailDialog';
import {
  EMPTY_PO_FILTERS,
  snapshotRows,
  type PoFilterState,
} from './scm-ops-helpers';
import { ScmPoTable } from './ScmPoTable';
import { ScmSnapshotTable } from './ScmSnapshotTable';
import { ScmSkuBreakdown } from './ScmSkuBreakdown';
import { ScmStalenessTable } from './ScmStalenessTable';

/**
 * scm operations section (TASK-PC-FE-008 — ADR-MONO-013 Phase 4 slice 2,
 * the SECOND non-IAM federated domain; completes Phase 4).
 *
 * STRICTLY READ-ONLY. The section renders:
 *   - procurement PO list (filters + pagination) + read-only PO detail;
 *   - inventory-visibility cross-node snapshot table;
 *   - inventory-visibility per-SKU breakdown (with the X-Cache freshness);
 *   - inventory-visibility node staleness panel.
 *
 * S5 (§ 2.4.6, NORMATIVE): EVERY inventory-visibility view renders the
 * producer `meta.warning` PROMINENTLY via <S5Warning>. It is never
 * stripped / hidden / de-emphasised — it is a required, surfaced field of
 * each view-model. The PO surface has no such warning (PO is the
 * authoritative procurement record).
 *
 * There is NO mutation affordance anywhere — no submit/confirm/cancel, no
 * idempotency, no reason capture, no confirm-to-mutate. PO write actions
 * are buyer/business mutations, explicitly out of console scope.
 *
 * Resilience (§ 2.5): 401 is handled by the server route (whole-session
 * re-login — not surfaced here); 403/404/429 → inline actionable;
 * 503/timeout → this section degrades only (the console shell + the
 * GAP/wms sections stay intact).
 *
 * ── MODULE SPLIT (TASK-PC-FE-144) ── this container owns ALL state (the
 * PO filter + query state, the 3 `useId`s, the SKU input/query state, the
 * PO detail target) and the `useScm*` queries + the seed/seeded logic; the
 * four read regions (PO list, snapshot table, per-SKU breakdown, node
 * staleness) are rendered by the prop-driven `ScmPoTable` /
 * `ScmSnapshotTable` / `ScmSkuBreakdown` / `ScmStalenessTable`
 * presentational children, and the PO filter shape + known-status list +
 * snapshot row normaliser live in `scm-ops-helpers.ts`.
 */

export interface ScmOpsScreenProps {
  poList: PoPage;
  snapshot: SnapshotResponse;
  staleness: StalenessResponse;
  /** Optional operator overview-snapshot slot rendered above the tables
   *  (TASK-PC-FE-167 — the server page computes `getScmOverviewState` and
   *  passes a `<ScmOverview>` node). Absent ⇒ no snapshot band. */
  overview?: React.ReactNode;
}

export function ScmOpsScreen({
  poList,
  snapshot,
  staleness,
  overview,
}: ScmOpsScreenProps) {
  const statusFid = useId();
  const supplierFid = useId();
  const skuFid = useId();

  // ── procurement PO list (filters + pagination) ──────────────────────
  const [poFilters, setPoFilters] =
    useState<PoFilterState>(EMPTY_PO_FILTERS);
  const [poQuery, setPoQuery] = useState<PoQueryParams>({
    page: 0,
    size: poList.size || SCM_DEFAULT_PAGE_SIZE,
  });

  const poSeeded =
    (poQuery.page ?? 0) === 0 &&
    !poQuery.status &&
    !poQuery.supplierId;

  const po = useScmPoList(poQuery, poSeeded ? poList : undefined);
  const poData = po.data ?? poList;

  const poApiError =
    po.error instanceof ApiError ? (po.error as ApiError) : null;
  const poForbidden = poApiError?.status === 403;
  const poRateLimited = poApiError?.code === 'RATE_LIMIT_EXCEEDED';
  const poDegraded =
    po.isError && !poForbidden && !poRateLimited;

  const [detail, setDetail] = useState<PurchaseOrder | null>(null);

  function submitPoFilters(e: React.FormEvent) {
    e.preventDefault();
    setPoQuery({
      status: poFilters.status.trim() || undefined,
      supplierId: poFilters.supplierId.trim() || undefined,
      page: 0,
      size: poList.size || SCM_DEFAULT_PAGE_SIZE,
    });
  }

  // ── inventory-visibility per-SKU breakdown (on demand) ──────────────
  const [skuInput, setSkuInput] = useState('');
  const [skuQuery, setSkuQuery] = useState<string | null>(null);
  const skuQ = useScmSkuBreakdown(skuQuery);
  const skuApiError =
    skuQ.error instanceof ApiError ? (skuQ.error as ApiError) : null;

  const snapRows = useMemo(() => snapshotRows(snapshot), [snapshot]);
  const stalenessRows = staleness.data;

  return (
    <section aria-labelledby="scm-heading">
      <h1 id="scm-heading" className="mb-2 text-2xl font-semibold">
        SCM 개요
      </h1>
      <p className="mb-6 text-sm text-muted-foreground">
        조달(발주) 조회 · 재고 가시성 (읽기 전용). scm 운영 표면을 콘솔
        안에서 조회합니다. 발주 쓰기 작업은 콘솔 범위가 아닙니다.
      </p>

      {/* Operator overview snapshot band (TASK-PC-FE-167) — server-rendered slot. */}
      {overview}

      <ScmPoTable
        statusFid={statusFid}
        supplierFid={supplierFid}
        filters={poFilters}
        onFiltersChange={setPoFilters}
        onSubmit={submitPoFilters}
        forbidden={poForbidden}
        rateLimited={poRateLimited}
        degraded={poDegraded}
        data={poData}
        query={poQuery}
        onDetail={setDetail}
        onPrevPage={() =>
          setPoQuery((q) => ({
            ...q,
            page: Math.max(0, (q.page ?? 0) - 1),
          }))
        }
        onNextPage={() =>
          setPoQuery((q) => ({ ...q, page: (q.page ?? 0) + 1 }))
        }
      />

      <ScmSnapshotTable warning={snapshot.meta.warning} rows={snapRows} />

      <ScmSkuBreakdown
        headerWarning={snapshot.meta.warning}
        skuFid={skuFid}
        skuInput={skuInput}
        onSkuInputChange={setSkuInput}
        onSubmit={(e) => {
          e.preventDefault();
          setSkuQuery(skuInput.trim() || null);
        }}
        apiError={skuApiError}
        result={skuQ.data}
      />

      <ScmStalenessTable
        warning={staleness.meta.warning}
        rows={stalenessRows}
      />

      <PoDetailDialog
        open={detail !== null}
        po={detail}
        onClose={() => setDetail(null)}
      />
    </section>
  );
}
