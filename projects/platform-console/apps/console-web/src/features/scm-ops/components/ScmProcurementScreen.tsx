'use client';

import { useId, useState } from 'react';
import { ApiError } from '@/shared/api/errors';
import { Button } from '@/shared/ui/Button';
import { useScmPoList } from '../hooks/use-scm-ops';
import {
  SCM_DEFAULT_PAGE_SIZE,
  type PoPage,
  type PurchaseOrder,
  type PoQueryParams,
} from '../api/types';
import {
  formatKstInstant,
  type PoListInitialFilter,
  type PoListUrlFilter,
} from '../api/po-list-params';
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
 *
 * TASK-MONO-810 — the screen starts from the URL's filter (`initialFilter`,
 * the one the server's first page was read with): a 개요 «실적» card lands
 * here with `submittedFrom`/`submittedTo` (its window) or `status`. The submit
 * window has no form field; it shows as a «제출 기간» line with a 해제 button,
 * stays on every re-query until cleared, and clearing also drops it from the
 * address (no navigation, no server re-read).
 */
export interface ScmProcurementScreenProps {
  poList: PoPage;
  /** The filter the server read `poList` with (from the URL). */
  initialFilter?: PoListInitialFilter;
  /** URL filter keys that were present but not applied (malformed). */
  ignoredParams?: PoListUrlFilter['ignored'];
}

const IGNORED_LABEL: Readonly<Record<PoListUrlFilter['ignored'][number], string>> = {
  status: '상태',
  submittedFrom: '제출 기간 시작',
  submittedTo: '제출 기간 끝',
  excludeStatus: '제외 상태',
};

/** Screen words for an excluded status (the card link sends CANCELED). */
const EXCLUDED_STATUS_LABEL: Readonly<Record<string, string>> = {
  CANCELED: '취소',
  DRAFT: '초안',
};

export function ScmProcurementScreen({
  poList,
  initialFilter = {},
  ignoredParams = [],
}: ScmProcurementScreenProps) {
  const statusFid = useId();
  const supplierFid = useId();

  // ── procurement PO list (filters + pagination) ──────────────────────
  const [poFilters, setPoFilters] = useState<PoFilterState>({
    ...EMPTY_PO_FILTERS,
    status: initialFilter.status ?? '',
  });
  const [poQuery, setPoQuery] = useState<PoQueryParams>({
    ...initialFilter,
    page: 0,
    size: poList.size || SCM_DEFAULT_PAGE_SIZE,
  });

  // `poList` answers exactly the query the server ran — the URL filter, page 0.
  const poSeeded =
    (poQuery.page ?? 0) === 0 &&
    !poQuery.supplierId &&
    (poQuery.status ?? undefined) === initialFilter.status &&
    (poQuery.submittedFrom ?? undefined) === initialFilter.submittedFrom &&
    (poQuery.submittedTo ?? undefined) === initialFilter.submittedTo &&
    (poQuery.excludeStatus ?? undefined) === initialFilter.excludeStatus;

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
    setPoQuery((q) => ({
      status: poFilters.status.trim() || undefined,
      supplierId: poFilters.supplierId.trim() || undefined,
      // The submit window has no form field — it rides along until cleared.
      submittedFrom: q.submittedFrom,
      submittedTo: q.submittedTo,
      excludeStatus: q.excludeStatus,
      page: 0,
      size: poList.size || SCM_DEFAULT_PAGE_SIZE,
    }));
  }

  /** Clears the card's filter as one unit: the submit window AND the
   *  exclusion that came with it (TASK-MONO-810 — the exclusion exists so the
   *  list equals a card's 발주 건수; it is not a filter the form offers). */
  function clearSubmittedPeriod() {
    setPoQuery((q) => ({
      ...q,
      submittedFrom: undefined,
      submittedTo: undefined,
      excludeStatus: undefined,
      page: 0,
    }));
    // Keep the address honest (a reload must not re-apply it) without a
    // navigation — Next keeps its router in sync with `history.replaceState`.
    if (typeof window !== 'undefined') {
      const url = new URL(window.location.href);
      url.searchParams.delete('submittedFrom');
      url.searchParams.delete('submittedTo');
      url.searchParams.delete('excludeStatus');
      window.history.replaceState(window.history.state, '', `${url.pathname}${url.search}`);
    }
  }

  const hasPeriod = Boolean(poQuery.submittedFrom || poQuery.submittedTo);
  const excluded = poQuery.excludeStatus
    ? (EXCLUDED_STATUS_LABEL[poQuery.excludeStatus] ?? poQuery.excludeStatus)
    : null;

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

      {ignoredParams.length > 0 && (
        <p
          role="status"
          data-testid="scm-po-url-ignored"
          className="mb-4 rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          주소에 담긴 필터 중 올바르지 않은 값(
          {ignoredParams.map((k) => IGNORED_LABEL[k]).join(' · ')})은 적용하지
          않았습니다.
        </p>
      )}

      {(hasPeriod || excluded) && (
        <div
          data-testid="scm-po-period-filter"
          className="mb-4 flex flex-wrap items-center justify-between gap-3 rounded-md border border-border bg-background px-4 py-3 text-sm"
        >
          <div>
            <p className="font-medium text-foreground" data-testid="scm-po-period-filter-range">
              {hasPeriod && (
                <>
                  제출 기간:{' '}
                  {poQuery.submittedFrom ? formatKstInstant(poQuery.submittedFrom) : '처음'}
                  {' – '}
                  {poQuery.submittedTo ? formatKstInstant(poQuery.submittedTo) : '지금'}
                  <span className="font-normal text-muted-foreground"> (한국 시간, 끝 시각 제외)</span>
                </>
              )}
              {hasPeriod && excluded && ' · '}
              {excluded && (
                <span data-testid="scm-po-period-filter-excluded">{excluded} 제외</span>
              )}
            </p>
            {hasPeriod && (
              <p className="mt-1 text-xs text-muted-foreground" data-testid="scm-po-period-filter-note">
                이 기간에 제출된 발주입니다.
              </p>
            )}
          </div>
          <Button
            variant="secondary"
            size="sm"
            onClick={clearSubmittedPeriod}
            data-testid="scm-po-period-clear"
          >
            {hasPeriod ? '제출 기간 해제' : '제외 해제'}
          </Button>
        </div>
      )}

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
