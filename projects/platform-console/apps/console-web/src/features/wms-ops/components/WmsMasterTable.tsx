'use client';

import type { Dispatch, FormEvent, SetStateAction } from 'react';
import { messageForCode } from '@/shared/api/errors';
import type { RefPage, RefQueryParams, RefType } from '../api/types';
import type { MasterFilterState } from './wms-master-helpers';
import { REF_TYPE_LABELS } from './wms-master-helpers';
import { WmsMasterFilters } from './WmsMasterFilters';
import { WmsMasterDataTable } from './WmsMasterDataTable';

/**
 * 마스터 참조 데이터 region — TASK-PC-FE-223 dedicated `/wms/master` screen.
 * Mirrors `WmsAsnTable`: free-standing (all state + handlers live in
 * `WmsMasterScreen`, this stays the prop-driven presentational child), owns
 * the forbidden/degraded/empty branching around the filters + data table.
 */
export interface WmsMasterTableProps {
  type: RefType;
  qFid: string;
  statusFid: string;
  filters: MasterFilterState;
  onFiltersChange: Dispatch<SetStateAction<MasterFilterState>>;
  onSubmit: (e: FormEvent) => void;
  forbidden: boolean;
  degraded: boolean;
  data: RefPage;
  query: RefQueryParams;
  onPrevPage: () => void;
  onNextPage: () => void;
}

export function WmsMasterTable({
  type,
  qFid,
  statusFid,
  filters,
  onFiltersChange,
  onSubmit,
  forbidden,
  degraded,
  data,
  query,
  onPrevPage,
  onNextPage,
}: WmsMasterTableProps) {
  const rows = data.content;

  return (
    <>
      <h2 className="mb-3 text-lg font-medium text-foreground">
        {REF_TYPE_LABELS[type]}
      </h2>
      <WmsMasterFilters
        qFid={qFid}
        statusFid={statusFid}
        filters={filters}
        onFiltersChange={onFiltersChange}
        onSubmit={onSubmit}
      />

      {forbidden ? (
        <div
          role="status"
          data-testid="wms-master-forbidden"
          className="mb-8 rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          {messageForCode('FORBIDDEN')}
        </div>
      ) : degraded ? (
        <div
          role="status"
          data-testid="wms-master-degraded"
          className="mb-8 rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          wms 마스터 참조 데이터를 일시적으로 불러올 수 없습니다. 콘솔의
          다른 기능은 계속 사용할 수 있습니다.
        </div>
      ) : rows.length === 0 ? (
        <p
          className="mb-8 text-sm text-muted-foreground"
          data-testid="wms-master-empty"
        >
          표시할 {REF_TYPE_LABELS[type]} 참조 데이터가 없습니다.
        </p>
      ) : (
        <WmsMasterDataTable
          type={type}
          data={data}
          query={query}
          onPrevPage={onPrevPage}
          onNextPage={onNextPage}
        />
      )}
    </>
  );
}
