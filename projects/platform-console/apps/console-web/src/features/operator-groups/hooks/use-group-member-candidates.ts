'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import { READ_QUERY_REFETCH } from '@/shared/api/query-options';
import {
  OperatorPageSchema,
  type OperatorPage,
  type OperatorSummary,
} from '@/shared/api/iam-operators-types';

/**
 * TASK-PC-FE-317 — operator candidates for the operator-group «멤버 추가»
 * picker. Client-side query against the SAME-ORIGIN proxy
 * (`GET /api/operators?tenantId=<group tenant>`) — NEVER `features/operators`
 * directly (architecture.md § Forbidden Dependencies bars a
 * `features/A → features/B` import; the shared, client-safe zod shapes in
 * `shared/api/iam-operators-types.ts` are the allowed import instead, mirror
 * of how `shared/api/rbac-catalog.ts` / `shared/api/iam-operators-read.ts`
 * were promoted for the same reason).
 *
 * Always queries the GROUP's `tenantId` (an explicit query param — see
 * `OperatorListParams.tenantId`), NEVER the console's active tenant (AC-1 /
 * task Failure Scenario — a SUPER_ADMIN viewing a group outside their active
 * tenant must not see the wrong tenant's roster).
 *
 * Pagination: size=100 (the producer max); `totalPages > 1` accumulates via
 * `loadMore()` so a >100-operator tenant is reachable without a silent
 * truncation (task Edge Cases) — the list has NO server-side search param
 * (task Background), so the caller's client-side filter only sees whatever
 * has been loaded so far.
 *
 * Fail-to-fallback (AC-5): `retry: false` — a single 403/503/network failure
 * flips `failed` true immediately (no silent multi-second retry delay before
 * the caller can fall back to the raw UUID input).
 */

const CANDIDATES_PAGE_SIZE = 100;

async function fetchCandidatesPage(
  tenantId: string,
  page: number,
): Promise<OperatorPage> {
  const qs = new URLSearchParams();
  qs.set('tenantId', tenantId);
  qs.set('page', String(page));
  qs.set('size', String(CANDIDATES_PAGE_SIZE));
  const raw = await apiClient.get<unknown>(`/api/operators?${qs.toString()}`);
  return OperatorPageSchema.parse(raw);
}

export interface GroupMemberCandidatesState {
  /** Accumulated operator rows across every page fetched so far. */
  items: OperatorSummary[];
  /** True while the FIRST page is in flight (nothing to render yet). */
  loading: boolean;
  /** True once a page fetch has failed — the caller's AC-5 fallback signal. */
  failed: boolean;
  /** True while a `loadMore()` page fetch (page > 0) is in flight. */
  loadingMore: boolean;
  /** True when a further page exists beyond what's loaded. */
  hasMore: boolean;
  /** Fetch the next page and append its rows. No-op if there's no more / a fetch is already in flight. */
  loadMore: () => void;
}

export function useGroupMemberCandidates(
  tenantId: string | null,
): GroupMemberCandidatesState {
  const [page, setPage] = useState(0);
  const [items, setItems] = useState<OperatorSummary[]>([]);
  const seenIds = useRef<Set<string>>(new Set());

  // A different group (different tenantId) was opened — start over.
  useEffect(() => {
    setPage(0);
    setItems([]);
    seenIds.current = new Set();
  }, [tenantId]);

  const query = useQuery({
    queryKey: ['operator-groups', 'member-candidates', tenantId, page],
    queryFn: () => fetchCandidatesPage(tenantId as string, page),
    enabled: tenantId !== null,
    retry: false,
    ...READ_QUERY_REFETCH,
  });

  useEffect(() => {
    if (!query.data) return;
    setItems((prev) => {
      const next = [...prev];
      for (const op of query.data!.content) {
        if (!seenIds.current.has(op.operatorId)) {
          seenIds.current.add(op.operatorId);
          next.push(op);
        }
      }
      return next;
    });
  }, [query.data]);

  const hasMore = query.data ? page + 1 < query.data.totalPages : false;

  const loadMore = useCallback(() => {
    if (!hasMore || query.isFetching) return;
    setPage((p) => p + 1);
  }, [hasMore, query.isFetching]);

  return {
    items,
    loading: page === 0 && query.isLoading,
    failed: query.isError,
    loadingMore: page > 0 && query.isFetching,
    hasMore,
    loadMore,
  };
}
