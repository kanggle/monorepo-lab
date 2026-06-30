'use client';

import {
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import { READ_QUERY_REFETCH } from '@/shared/api/query-options';
import {
  DiscrepancySchema,
  type Discrepancy,
  DiscrepanciesResponseSchema,
  type DiscrepanciesResponse,
  type DiscrepanciesQueryParams,
  type ResolveDiscrepancyBody,
  StatementSchema,
  type Statement,
} from '../api/types';
import { LEDGER_KEY, clampSize } from './use-ledger-shared';

/**
 * Ledger-ops reconciliation read + the ledger surface's ONLY mutation
 * (TASK-PC-FE-148 split). The discrepancy queue / detail / statement reads
 * are pure reads; `useResolveDiscrepancy` (TASK-PC-FE-073) is the EXACTLY ONE
 * mutation hook on the ledger surface. Every call goes to the same-origin
 * `/api/ledger/**` proxy; the proxy attaches the HttpOnly IAM OIDC access
 * token server-side (§ 2.3 / § 2.4.7.1 reuse). `retry: false` — a failure
 * surfaces immediately (no client retry storm; no 429 backoff). Behavior-
 * preserving extraction — names / signatures / queryKeys / onSuccess
 * invalidation / call order verbatim from the pre-split `use-ledger-ops`.
 */

// --- reconciliation discrepancies (queue) read ---------------------------

export function discrepanciesKey(params: DiscrepanciesQueryParams) {
  return [
    LEDGER_KEY,
    'discrepancies',
    params.status ?? null,
    Math.max(0, params.page ?? 0),
    clampSize(params.size),
  ] as const;
}

export function buildDiscrepanciesQs(
  params: DiscrepanciesQueryParams,
): string {
  const qs = new URLSearchParams();
  if (params.status) qs.set('status', params.status);
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(clampSize(params.size)));
  return qs.toString();
}

async function fetchDiscrepancies(
  params: DiscrepanciesQueryParams,
): Promise<DiscrepanciesResponse> {
  const raw = await apiClient.get<unknown>(
    `/api/ledger/reconciliation/discrepancies?${buildDiscrepanciesQs(params)}`,
  );
  return DiscrepanciesResponseSchema.parse(raw);
}

export function useDiscrepancies(
  params: DiscrepanciesQueryParams,
  initial?: DiscrepanciesResponse,
) {
  const seeded =
    initial !== undefined &&
    (params.page ?? 0) === 0 &&
    params.status === 'OPEN';
  return useQuery({
    queryKey: discrepanciesKey(params),
    queryFn: () => fetchDiscrepancies(params),
    initialData: seeded ? initial : undefined,
    staleTime: seeded ? 30_000 : 0,
    refetchOnMount: seeded ? false : true,
    ...READ_QUERY_REFETCH,
    retry: false,
  });
}

// --- reconciliation discrepancy detail read ------------------------------

export function discrepancyKey(id: string | null) {
  return [LEDGER_KEY, 'discrepancy', id ?? ''] as const;
}

async function fetchDiscrepancy(id: string): Promise<Discrepancy> {
  const raw = await apiClient.get<unknown>(
    `/api/ledger/reconciliation/discrepancies/${encodeURIComponent(id)}`,
  );
  return DiscrepancySchema.parse(raw);
}

export function useDiscrepancy(id: string | null) {
  return useQuery({
    queryKey: discrepancyKey(id),
    queryFn: () => fetchDiscrepancy(id as string),
    enabled: Boolean(id && id.trim()),
    staleTime: 30_000,
    refetchOnMount: false,
    ...READ_QUERY_REFETCH,
    retry: false,
  });
}

// --- reconciliation statement read (id-driven; TASK-PC-FE-075) -----------

export function statementKey(id: string | null) {
  return [LEDGER_KEY, 'statement', id ?? ''] as const;
}

async function fetchStatement(id: string): Promise<Statement> {
  const raw = await apiClient.get<unknown>(
    `/api/ledger/reconciliation/statements/${encodeURIComponent(id)}`,
  );
  return StatementSchema.parse(raw);
}

/**
 * `useStatement` — reads a reconciliation statement by id. READ-ONLY. The
 * same-origin proxy attaches the domain-facing IAM OIDC access token
 * server-side — NEVER the operator token (§ 2.4.7.1 reuse).
 * `retry: false` / no-refetch-storm posture, same as the other ledger reads.
 * `initialData` is used when the server-seeded `initial` matches the
 * requested id (to avoid a double-fetch on SSR→CSR transition). No 429
 * branch (the ledger has no documented 429). Adds NO mutation artifact.
 */
export function useStatement(id: string | null, initial?: Statement) {
  const seeded = initial !== undefined && Boolean(id);
  return useQuery({
    queryKey: statementKey(id),
    queryFn: () => fetchStatement(id as string),
    enabled: Boolean(id && id.trim()),
    initialData: seeded ? initial : undefined,
    staleTime: 30_000,
    refetchOnMount: false,
    ...READ_QUERY_REFETCH,
    retry: false,
  });
}

// --- reconciliation discrepancy RESOLVE (the ledger's ONLY mutation) ------
//
// TASK-PC-FE-073 — POSTs to the same-origin proxy
//   /api/ledger/reconciliation/discrepancies/{id}/resolve
// with a body `{ resolutionType, note }`. NO `Idempotency-Key` (the producer
// defines none for resolve — the `409 RECONCILIATION_ALREADY_RESOLVED` state
// guard is the double-submit defence), NO `X-Operator-Reason` (the reason
// rides in the body `note`). `retry: false` — a failure surfaces immediately
// (no client retry storm; no 429 backoff). `onSuccess` invalidates the
// discrepancy list (any status/page) + the resolved row's detail so the
// queue/detail reflect `RESOLVED` + `resolution`.

export interface ResolveDiscrepancyArgs {
  id: string;
  input: ResolveDiscrepancyBody;
}

export function useResolveDiscrepancy() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, input }: ResolveDiscrepancyArgs) => {
      const raw = await apiClient.post<unknown>(
        `/api/ledger/reconciliation/discrepancies/${encodeURIComponent(id)}/resolve`,
        { resolutionType: input.resolutionType, note: input.note },
      );
      return DiscrepancySchema.parse(raw);
    },
    retry: false,
    onSuccess: (_data, { id }) => {
      // Invalidate the whole discrepancy queue (any status / page) + the
      // resolved row's detail read — the queue/detail re-fetch and reflect
      // RESOLVED + the resolution sub-object.
      qc.invalidateQueries({ queryKey: [LEDGER_KEY, 'discrepancies'] });
      qc.invalidateQueries({ queryKey: discrepancyKey(id) });
    },
  });
}
