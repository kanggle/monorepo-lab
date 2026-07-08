'use client';

import { useId, useState } from 'react';
import { ApiError } from '@/shared/api/errors';
import { useScmPoList } from '../hooks/use-scm-ops';
import {
  SCM_DEFAULT_PAGE_SIZE,
  type PoPage,
  type PurchaseOrder,
  type PoQueryParams,
} from '../api/types';
import { PoDetailDialog } from './PoDetailDialog';
import { EMPTY_PO_FILTERS, type PoFilterState } from './scm-ops-helpers';
import { ScmPoTable } from './ScmPoTable';

/**
 * scm 조달 (procurement PO list) screen — split out of the former combined
 * ScmOpsScreen (TASK-PC-FE-220; the read section was TASK-PC-FE-008, the
 * god-file split TASK-PC-FE-144). STRICTLY READ-ONLY.
 *
 * Renders the procurement PO list (status/supplier filters + pagination)
 * and a read-only PO detail dialog. There is NO mutation affordance
 * anywhere — no submit/confirm/cancel, no idempotency, no reason capture,
 * no confirm-to-mutate. PO write actions are buyer/business mutations,
 * explicitly out of console scope.
 *
 * Resilience (§ 2.5): 401 is handled by the server route (whole-session
 * re-login — not surfaced here); 403/404/429 → inline actionable;
 * 503/timeout on a re-query → the PO block degrades only
 * (`scm-po-degraded`); the console shell + other sections stay intact.
 *
 * This container owns the PO filter + query state, the two `useId`s and
 * the PO detail target; the read region is rendered by the prop-driven
 * `ScmPoTable` presentational child (the PO filter shape + known-status
 * list live in `scm-ops-helpers.ts`).
 */
export interface ScmProcurementScreenProps {
  poList: PoPage;
}

export function ScmProcurementScreen({ poList }: ScmProcurementScreenProps) {
  const statusFid = useId();
  const supplierFid = useId();

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

  return (
    <section aria-labelledby="scm-procurement-heading">
      <h1
        id="scm-procurement-heading"
        className="mb-2 text-2xl font-semibold"
      >
        SCM 조달
      </h1>
      <p className="mb-6 text-sm text-muted-foreground">
        조달(발주) 조회 (읽기 전용). 발주 쓰기 작업은 콘솔 범위가 아닙니다.
      </p>

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

      <PoDetailDialog
        open={detail !== null}
        po={detail}
        onClose={() => setDetail(null)}
      />
    </section>
  );
}
