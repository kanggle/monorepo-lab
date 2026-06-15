'use client';

import {
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import { READ_QUERY_REFETCH } from '@/shared/api/query-options';
import { clampPageSize } from '@/shared/lib/pagination';
import {
  SuggestionPageSchema,
  type SuggestionPage,
  ApproveResultSchema,
  type ApproveResult,
  DismissResultSchema,
  type DismissResult,
  type SuggestionQueryParams,
  REPL_DEFAULT_PAGE_SIZE,
  REPL_MAX_PAGE_SIZE,
} from '../api/types';

/**
 * Client-side scm-replenishment hooks (architecture.md § Server vs Client
 * Components — React Query is client-only). Every call goes to the same-origin
 * `/api/scm/demand-planning/**` proxy (the typed API client's single backend
 * entry point); the proxy attaches the HttpOnly **domain-facing IAM OIDC
 * token** server-side — the browser never reads a token or calls scm directly
 * (contract § 2.3 / § 2.4.6.1). The § 2.4.5/§ 2.4.6 per-domain credential rule
 * is reused, NOT re-derived.
 *
 * Rate-limit discipline (§ 2.4.6.1 reusing § 2.4.6 / task Edge Case "429"):
 * the scm gateway is rate-limited. There is NO tight auto-refetch loop /
 * refetchInterval / refetchOnWindowFocus — a re-query is a filter/page change
 * (a new queryKey) or an explicit user action (a mutation success
 * invalidation). The server proxy already honours a 429 `Retry-After` with ONE
 * bounded backoff; the client pins `retry: false` to never storm the gateway.
 *
 * Mutation discipline (§ 2.4.6.1): approve / dismiss carry an OPTIONAL
 * note/reason in the BODY — NO `Idempotency-Key`, NO `X-Operator-Reason`
 * (demand-planning-api defines neither; the producer is idempotent by
 * suggestion state). On success the list + detail are invalidated so the
 * `SUGGESTED → MATERIALIZED|DISMISSED` transition reflects without a manual
 * reload.
 */

const REPL_KEY = 'scm-replenishment';

const clampSize = (size?: number): number =>
  clampPageSize(size, REPL_DEFAULT_PAGE_SIZE, REPL_MAX_PAGE_SIZE);

// --- suggestions list read -----------------------------------------------

export function suggestionsKey(params: SuggestionQueryParams) {
  return [
    REPL_KEY,
    'suggestions',
    params.status ?? null,
    params.skuCode ?? null,
    Math.max(0, params.page ?? 0),
    clampSize(params.size),
  ] as const;
}

export function buildSuggestionsQs(params: SuggestionQueryParams): string {
  const qs = new URLSearchParams();
  if (params.status) qs.set('status', params.status);
  if (params.skuCode) qs.set('skuCode', params.skuCode);
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(clampSize(params.size)));
  return qs.toString();
}

async function fetchSuggestions(
  params: SuggestionQueryParams,
): Promise<SuggestionPage> {
  const raw = await apiClient.get<unknown>(
    `/api/scm/demand-planning/suggestions?${buildSuggestionsQs(params)}`,
  );
  return SuggestionPageSchema.parse(raw);
}

export function useSuggestions(
  params: SuggestionQueryParams,
  initial?: SuggestionPage,
) {
  const seeded =
    initial !== undefined &&
    (params.page ?? 0) === 0 &&
    !params.status &&
    !params.skuCode;
  return useQuery({
    queryKey: suggestionsKey(params),
    queryFn: () => fetchSuggestions(params),
    initialData: seeded ? initial : undefined,
    // Seeded from the server render ⇒ fresh. A filter/page change is a new
    // queryKey → one fresh proxy call. NO refetch interval / focus refetch /
    // client retry — the gateway is rate-limited (§ 2.4.6.1).
    staleTime: seeded ? 30_000 : 0,
    refetchOnMount: seeded ? false : true,
    ...READ_QUERY_REFETCH,
    retry: false,
  });
}

// --- operator actions (approve / dismiss) --------------------------------
//   Optional note/reason in the BODY. NO Idempotency-Key, NO X-Operator-Reason.

export interface ApproveArgs {
  id: string;
  /** OPTIONAL operator note — rides in the request body, never a header. */
  note?: string;
}

export function useApproveSuggestion() {
  const qc = useQueryClient();
  return useMutation<ApproveResult, unknown, ApproveArgs>({
    mutationFn: async ({ id, note }) => {
      const raw = await apiClient.post<unknown>(
        `/api/scm/demand-planning/suggestions/${encodeURIComponent(id)}/approve`,
        // OPTIONAL body — only the note when present (no header scaffolding).
        note && note.trim() ? { note: note.trim() } : {},
      );
      return ApproveResultSchema.parse(raw);
    },
    onSuccess: (_data, { id }) => {
      // The status transition (SUGGESTED → MATERIALIZED) must reflect without a
      // manual reload — invalidate the list + this suggestion's detail.
      qc.invalidateQueries({ queryKey: [REPL_KEY, 'suggestions'] });
      qc.invalidateQueries({ queryKey: [REPL_KEY, 'detail', id] });
    },
  });
}

export interface DismissArgs {
  id: string;
  /** OPTIONAL operator reason — rides in the request body, never a header. */
  reason?: string;
}

export function useDismissSuggestion() {
  const qc = useQueryClient();
  return useMutation<DismissResult, unknown, DismissArgs>({
    mutationFn: async ({ id, reason }) => {
      const raw = await apiClient.post<unknown>(
        `/api/scm/demand-planning/suggestions/${encodeURIComponent(id)}/dismiss`,
        reason && reason.trim() ? { reason: reason.trim() } : {},
      );
      return DismissResultSchema.parse(raw);
    },
    onSuccess: (_data, { id }) => {
      qc.invalidateQueries({ queryKey: [REPL_KEY, 'suggestions'] });
      qc.invalidateQueries({ queryKey: [REPL_KEY, 'detail', id] });
    },
  });
}
