'use client';

import type { FormEvent } from 'react';
import { Button } from '@/shared/ui/Button';
import { ApiError, messageForCode } from '@/shared/api/errors';
import type { SkuBreakdown, SnapshotResponse } from '../api/types';

/**
 * Inventory-visibility per-SKU breakdown region of the scm ops screen
 * (TASK-PC-FE-144 split) — the section S5 `meta.warning` + the on-demand
 * SKU lookup form + the result (which carries its OWN required S5
 * `meta.warning`, surfaced, never stripped) or the error / prompt notice.
 * Pure presentation: all state + handlers live in the `ScmOpsScreen`
 * container and arrive via props. STRICTLY READ-ONLY.
 */
import { S5Warning } from './S5Warning';

export interface ScmSkuBreakdownProps {
  /** The section-level S5 warning (from the snapshot view-model). */
  headerWarning: SnapshotResponse['meta']['warning'];
  skuFid: string;
  skuInput: string;
  onSkuInputChange: (value: string) => void;
  onSubmit: (e: FormEvent) => void;
  apiError: ApiError | null;
  result: SkuBreakdown | undefined;
}

export function ScmSkuBreakdown({
  headerWarning,
  skuFid,
  skuInput,
  onSkuInputChange,
  onSubmit,
  apiError,
  result,
}: ScmSkuBreakdownProps) {
  return (
    <>
      {/* ── inventory-visibility: per-SKU breakdown (S5 + X-Cache) ────── */}
      <h2 className="mb-3 text-lg font-medium text-foreground">
        재고 가시성 — SKU별 분포
      </h2>
      <S5Warning warning={headerWarning} />
      <form
        onSubmit={onSubmit}
        className="mb-4 flex items-end gap-3"
        role="search"
        aria-label="SKU 분포 조회"
      >
        <div className="flex-1">
          <label
            htmlFor={skuFid}
            className="block text-sm font-medium text-foreground"
          >
            SKU 코드
          </label>
          <input
            id={skuFid}
            type="text"
            value={skuInput}
            onChange={(e) => onSkuInputChange(e.target.value)}
            data-testid="scm-sku-input"
            className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          />
        </div>
        <Button type="submit" data-testid="scm-sku-submit">
          조회
        </Button>
      </form>
      {apiError ? (
        <div
          role="status"
          data-testid="scm-sku-error"
          className="mb-8 rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          {apiError.status === 403
            ? messageForCode('TENANT_FORBIDDEN')
            : apiError.code === 'RATE_LIMIT_EXCEEDED'
              ? messageForCode('RATE_LIMIT_EXCEEDED')
              : messageForCode(
                  apiError.code,
                  'SKU 분포를 불러올 수 없습니다.',
                )}
        </div>
      ) : result ? (
        <div className="mb-8" data-testid="scm-sku-result">
          {/* The per-SKU response carries its OWN required S5 meta.warning
              — surfaced prominently, never stripped. */}
          <S5Warning warning={result.meta.warning} />
          <p className="mb-2 text-sm text-foreground">
            <span className="font-medium">{result.data.sku}</span> · 총{' '}
            {result.data.totalQuantity ?? '—'}
          </p>
          <table
            className="data-table"
            data-testid="scm-sku-table"
          >
            <caption className="sr-only">SKU별 노드 분포</caption>
            <thead>
              <tr className="border-b border-border text-left">
                <th scope="col" className="p-2">
                  노드
                </th>
                <th scope="col" className="p-2">
                  수량
                </th>
                <th scope="col" className="p-2">
                  신선도
                </th>
              </tr>
            </thead>
            <tbody>
              {result.data.nodes.map((n, i) => (
                <tr
                  key={`${n.nodeId}-${i}`}
                  data-testid={`scm-sku-node-${i}`}
                  className="border-b border-border"
                >
                  <td className="p-2">{n.nodeId}</td>
                  <td className="p-2">{n.quantity ?? '—'}</td>
                  <td className="p-2">{n.staleness ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <p
          className="mb-8 text-sm text-muted-foreground"
          data-testid="scm-sku-prompt"
        >
          조회할 SKU 코드를 입력하세요.
        </p>
      )}
    </>
  );
}
