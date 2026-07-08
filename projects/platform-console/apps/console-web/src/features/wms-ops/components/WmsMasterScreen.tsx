'use client';

import { useId, useMemo, useState } from 'react';
import { ApiError } from '@/shared/api/errors';
import { useWmsRefs } from '../hooks/use-wms-ops';
import {
  WMS_DEFAULT_PAGE_SIZE,
  type RefPage,
  type RefQueryParams,
  type RefType,
} from '../api/types';
import { WmsMasterTypeTabs } from './WmsMasterTypeTabs';
import { WmsMasterTable } from './WmsMasterTable';
import { WmsLagHint } from './WmsLagHint';
import { EMPTY_MASTER_FILTERS, type MasterFilterState } from './wms-master-helpers';

/**
 * wms **마스터**(master reference data) section — TASK-PC-FE-223, the
 * console's first surface for the read-model's `GET /dashboard/refs/{type}`
 * (§ 1.7) — a producer read the client (`listRefs`, `wms-refs-api.ts`)
 * already exported since the § 1.7 client split but with ZERO consumers.
 * Mirrors `WmsInboundScreen`: this container owns the ref-type tab
 * selection, the `q`/`status` filter/query state, and the pagination
 * handlers; `WmsMasterTable`/`WmsMasterDataTable` stay the prop-driven
 * presentational children.
 *
 * Tab set = every producer-supported `{type}` (`admin-service-api.md`
 * § 1.7: warehouses|zones|locations|skus|lots|partners) — the console never
 * invents a type the read-model doesn't document.
 *
 * READ-ONLY (Out of Scope, task § Out of Scope): master data
 * create/update/delete is NOT projected as a write on this admin read-model
 * surface — the raw `master-service` owns that SoT, out of this task's
 * console-admin-read-model consumption convention.
 *
 * Resilience (§ 2.5, mirrors `WmsInboundScreen`): 401 is handled by the
 * server route (whole-session re-login); 403 → inline forbidden; 503/timeout
 * → this section degrades only (the console shell stays intact).
 */
export interface WmsMasterScreenProps {
  refs: RefPage;
  refType: RefType;
  /** NON-blocking eventual-consistency hint (seconds), or null. */
  lagSeconds: number | null;
}

export function WmsMasterScreen({
  refs,
  refType,
  lagSeconds,
}: WmsMasterScreenProps) {
  const qFid = useId();
  const statusFid = useId();

  const [selectedType, setSelectedType] = useState<RefType>(refType);
  const [filters, setFilters] = useState<MasterFilterState>(
    EMPTY_MASTER_FILTERS,
  );
  const [query, setQuery] = useState<RefQueryParams>({
    page: 0,
    size: refs.page.size || WMS_DEFAULT_PAGE_SIZE,
  });

  const seeded =
    selectedType === refType &&
    (query.page ?? 0) === 0 &&
    !query.q &&
    !query.status;

  const refsQuery = useWmsRefs(selectedType, query, seeded ? refs : undefined);
  const data = refsQuery.data ?? refs;

  const apiError =
    refsQuery.error instanceof ApiError ? (refsQuery.error as ApiError) : null;
  const forbidden = apiError?.status === 403;
  const degraded =
    refsQuery.isError && (!apiError || apiError.status >= 500) && !forbidden;

  function onSelectType(type: RefType) {
    setSelectedType(type);
    setFilters(EMPTY_MASTER_FILTERS);
    setQuery({ page: 0, size: refs.page.size || WMS_DEFAULT_PAGE_SIZE });
  }

  function submitFilters(e: React.FormEvent) {
    e.preventDefault();
    setQuery({
      q: filters.q.trim() || undefined,
      status: filters.status.trim() || undefined,
      page: 0,
      size: refs.page.size || WMS_DEFAULT_PAGE_SIZE,
    });
  }

  const lagBanner = useMemo(() => {
    if (lagSeconds === null || lagSeconds <= 0) return null;
    return `데이터가 약 ${Math.round(lagSeconds)}초 지연될 수 있습니다 (읽기 모델은 최종 일관성 — 표시값이 잠시 과거일 수 있습니다).`;
  }, [lagSeconds]);

  return (
    <section aria-labelledby="wms-master-heading">
      <h1 id="wms-master-heading" className="mb-2 text-2xl font-semibold">
        WMS 마스터
      </h1>
      <p className="mb-6 text-sm text-muted-foreground">
        창고 · 구역 · 로케이션 · SKU · Lot · 거래처 참조 데이터 조회 (읽기
        전용).
      </p>

      <WmsLagHint testid="wms-lag-hint" message={lagBanner} />

      <WmsMasterTypeTabs selected={selectedType} onSelect={onSelectType} />

      <WmsMasterTable
        type={selectedType}
        qFid={qFid}
        statusFid={statusFid}
        filters={filters}
        onFiltersChange={setFilters}
        onSubmit={submitFilters}
        forbidden={forbidden}
        degraded={degraded}
        data={data}
        query={query}
        onPrevPage={() =>
          setQuery((q) => ({ ...q, page: Math.max(0, (q.page ?? 0) - 1) }))
        }
        onNextPage={() => setQuery((q) => ({ ...q, page: (q.page ?? 0) + 1 }))}
      />
    </section>
  );
}
