'use client';

import { useId, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { Button } from '@/shared/ui/Button';
import { ApiError, messageForCode } from '@/shared/api/errors';
import {
  usePromotions,
  useDeletePromotion,
} from '../hooks/use-ecommerce-promotions';
import {
  PROMOTION_DEFAULT_PAGE_SIZE,
  PROMOTION_STATUS_VALUES,
  type PromotionList,
  type PromotionListParams,
} from '../api/types';
import { ConfirmDialog } from './ConfirmDialog';
import { PromotionsTable } from './PromotionsTable';

/**
 * ecommerce promotion operations list section (TASK-PC-FE-086 — ADR-031 Phase 3b).
 *
 * Server-rendered initial page is passed in; client re-query handles
 * status-filter / pagination. Per-row actions: 상세(drill) / 삭제(confirm-gated, 204).
 * "새 프로모션" links to /new.
 *
 * Resilience (§ 2.5): 403/422 → inline; 503/timeout → this section degrades only.
 *
 * TASK-PC-FE-200: the list table + pagination are extracted into
 * {@link PromotionsTable} (presentational); this container keeps ALL state —
 * filter/pagination query, seed fallback, list-state branching, and the
 * confirm-gated delete.
 */

export interface PromotionsScreenProps {
  promotions: PromotionList;
}

const STATUS_FILTER_OPTIONS = ['', ...PROMOTION_STATUS_VALUES] as const;

export function PromotionsScreen({ promotions }: PromotionsScreenProps) {
  const router = useRouter();
  const statusFid = useId();

  const [statusFilter, setStatusFilter] = useState('');
  const [query, setQuery] = useState<PromotionListParams>({
    page: 0,
    size: promotions.size || PROMOTION_DEFAULT_PAGE_SIZE,
  });

  const seeded = (query.page ?? 0) === 0 && !query.status;
  const listQ = usePromotions(query, seeded ? promotions : undefined);
  // Only the seeded (page 0, no filter) query may fall back to the server-rendered
  // `promotions` seed. For a filtered/paginated query, falling back to the seed would
  // flash the full unfiltered list while the new query is still in flight — instead
  // we render a loading placeholder until the real result lands.
  const data = seeded ? listQ.data ?? promotions : listQ.data;
  const loading = data === undefined;

  const apiError =
    listQ.error instanceof ApiError ? (listQ.error as ApiError) : null;
  const forbidden = apiError?.status === 403;
  const degraded =
    listQ.isError && (!apiError || apiError.status >= 500) && !forbidden;

  // --- delete --------------------------------------------------------------
  const del = useDeletePromotion();
  const [toDelete, setToDelete] = useState<{
    id: string;
    name: string;
  } | null>(null);
  const [delError, setDelError] = useState<string | null>(null);

  function confirmDelete() {
    if (!toDelete) return;
    setDelError(null);
    del.mutate(toDelete.id, {
      onSuccess: () => {
        setToDelete(null);
        router.refresh();
      },
      onError: (e) => {
        const code = e instanceof ApiError ? e.code : 'SERVICE_UNAVAILABLE';
        setDelError(messageForCode(code, '프로모션을 삭제하지 못했습니다.'));
      },
    });
  }

  function submitFilter(e: React.FormEvent) {
    e.preventDefault();
    setQuery({
      status: statusFilter || undefined,
      page: 0,
      size: promotions.size || PROMOTION_DEFAULT_PAGE_SIZE,
    });
  }

  const rows = data?.content ?? [];
  const totalPages = data
    ? Math.max(1, Math.ceil(data.totalElements / (data.size || 20)))
    : 1;

  return (
    <section aria-labelledby="ecommerce-promotions-heading">
      <div className="mb-2 flex items-center justify-between">
        <h1
          id="ecommerce-promotions-heading"
          className="text-2xl font-semibold"
        >
          E-Commerce 프로모션
        </h1>
        <Link
          href="/ecommerce/promotions/new"
          data-testid="promotion-new-link"
        >
          <Button>새 프로모션</Button>
        </Link>
      </div>
      <p className="mb-6 text-sm text-muted-foreground">
        프로모션 목록 · 상세 · 생성 / 수정 / 삭제 + 쿠폰 발급.
      </p>

      <form
        onSubmit={submitFilter}
        className="mb-4 flex flex-wrap items-end gap-3"
        role="search"
        aria-label="프로모션 필터"
      >
        <div>
          <label
            htmlFor={statusFid}
            className="block text-sm font-medium text-foreground"
          >
            상태
          </label>
          <select
            id={statusFid}
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
            data-testid="promotion-status-filter"
            className="mt-1 rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            {STATUS_FILTER_OPTIONS.map((s) => (
              <option key={s || 'all'} value={s}>
                {s || '전체'}
              </option>
            ))}
          </select>
        </div>
        <Button type="submit" data-testid="promotion-filter-submit">
          조회
        </Button>
      </form>

      {forbidden ? (
        <div
          role="status"
          data-testid="promotion-forbidden"
          className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          {messageForCode('FORBIDDEN')}
        </div>
      ) : degraded ? (
        <div
          role="status"
          data-testid="promotion-degraded"
          className="rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
        >
          ecommerce 프로모션 정보를 일시적으로 불러올 수 없습니다. 콘솔의 다른
          기능은 계속 사용할 수 있습니다.
        </div>
      ) : loading ? (
        <p
          className="text-sm text-muted-foreground"
          data-testid="promotion-loading"
        >
          조회 중…
        </p>
      ) : rows.length === 0 ? (
        <p
          className="text-sm text-muted-foreground"
          data-testid="promotion-empty"
        >
          표시할 프로모션이 없습니다.
        </p>
      ) : (
        <PromotionsTable
          rows={rows}
          onDelete={(promotion) => {
            setDelError(null);
            setToDelete(promotion);
          }}
          pagination={{
            prevDisabled: (query.page ?? 0) <= 0,
            nextDisabled: (data?.page ?? 0) + 1 >= totalPages,
            pageInfo: `${(data?.page ?? 0) + 1} / ${totalPages} 페이지 · 총 ${data?.totalElements ?? 0}건`,
            onPrev: () =>
              setQuery((q) => ({
                ...q,
                page: Math.max(0, (q.page ?? 0) - 1),
              })),
            onNext: () =>
              setQuery((q) => ({ ...q, page: (q.page ?? 0) + 1 })),
          }}
        />
      )}

      <ConfirmDialog
        open={toDelete !== null}
        title="프로모션을 삭제할까요?"
        description={
          toDelete
            ? `"${toDelete.name}" 프로모션을 삭제합니다. 이 작업은 되돌릴 수 없습니다.`
            : ''
        }
        confirmLabel="삭제"
        tone="destructive"
        pending={del.isPending}
        errorMessage={delError}
        onConfirm={confirmDelete}
        onCancel={() => {
          setToDelete(null);
          setDelError(null);
        }}
      />
    </section>
  );
}
