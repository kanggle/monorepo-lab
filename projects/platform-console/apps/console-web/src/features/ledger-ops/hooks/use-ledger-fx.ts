'use client';

import {
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import { READ_QUERY_REFETCH } from '@/shared/api/query-options';
import {
  PositionLotsResponseSchema,
  type PositionLotsResponse,
  FxRatesResponseSchema,
  type FxRatesResponse,
  FxRateHistoryResponseSchema,
  type FxRateHistoryResponse,
  FxRatesRefreshResponseSchema,
  type FxRatesRefreshResponse,
  FX_HISTORY_DEFAULT_LIMIT,
  FX_HISTORY_MAX_LIMIT,
} from '../api/types';
import { LEDGER_KEY } from './use-ledger-shared';

/**
 * Ledger-ops FX hooks (TASK-PC-FE-148 split) — open position lots
 * (TASK-PC-FE-091), the FX rate feed dashboard (TASK-PC-FE-092), the per-pair
 * rate history drill (TASK-PC-FE-104), and the operator manual-refresh
 * mutation (TASK-MONO-300). The reads are pure reads; `useRefreshFxRates` is
 * an operator mutation that re-fetches the feed cache `onSuccess`. Every call
 * goes to the same-origin `/api/ledger/**` proxy; the proxy attaches the
 * HttpOnly IAM OIDC access token server-side (§ 2.3 / § 2.4.7.1 reuse).
 * `rate` is a decimal string (F5 — NEVER a Number); `ageSeconds` / `limit`
 * are plain integer counts (NOT money). `retry: false` / no refetch-storm
 * posture. Behavior-preserving extraction — names / signatures / queryKeys /
 * clamps / onSuccess invalidation verbatim from the pre-split `use-ledger-ops`.
 */

// --- FX position open-lots read (id-driven; TASK-PC-FE-091) ---------------

export function positionLotsKey(code: string | null, currency: string | null) {
  return [LEDGER_KEY, 'position-lots', code ?? '', currency ?? ''] as const;
}

async function fetchPositionLots(
  code: string,
  currency: string,
): Promise<PositionLotsResponse> {
  const raw = await apiClient.get<unknown>(
    `/api/ledger/settlements/${encodeURIComponent(code)}/${encodeURIComponent(currency)}/lots`,
  );
  return PositionLotsResponseSchema.parse(raw);
}

/**
 * `usePositionLots` — reads the open FX acquisition lots for one
 * `(account, currency)` position. READ-ONLY. The same-origin proxy attaches
 * the domain-facing IAM OIDC access token server-side — NEVER the operator
 * token (§ 2.4.7.1 reuse). `enabled`-gated: the query only fires once BOTH a
 * non-empty account code AND a non-empty currency are supplied (the lookup
 * form submit gates it). `retry: false` / no-refetch-storm posture, same as
 * the other ledger reads. No 429 branch (the ledger has no documented 429).
 * Adds NO mutation artifact. An empty position is a normal success
 * (`lots: []`) — never an error.
 */
export function usePositionLots(
  code: string | null,
  currency: string | null,
  enabled = true,
) {
  return useQuery({
    queryKey: positionLotsKey(code, currency),
    queryFn: () => fetchPositionLots(code as string, currency as string),
    enabled:
      enabled && Boolean(code && code.trim() && currency && currency.trim()),
    staleTime: 30_000,
    refetchOnMount: false,
    ...READ_QUERY_REFETCH,
    retry: false,
  });
}

// --- FX 환율 피드 대시보드 (global read; TASK-PC-FE-092) --------------------
//
// GET /api/finance/ledger/fx-rates (FIN-BE-033). No input parameters —
// global list; the query is always enabled (no `enabled` gate). `staleTime`
// matches the lots read (30 s). No refetchInterval — the operator manually
// refreshes via the UI button. `rate` is a decimal string (F5 — NEVER a
// Number); `ageSeconds` is a plain integer duration (count, not money).

export function fxRatesKey() {
  return [LEDGER_KEY, 'fx-rates'] as const;
}

async function fetchFxRates(): Promise<FxRatesResponse> {
  const raw = await apiClient.get<unknown>('/api/ledger/fx-rates');
  return FxRatesResponseSchema.parse(raw);
}

/**
 * `useFxRates` — reads the FX feed cache. READ-ONLY. No input form (global
 * dashboard), but gated by `enabled` so the query only fires when its tab is
 * active — a hidden panel must not fetch on mount (and must not pollute other
 * tabs' request assertions). The same-origin proxy attaches the domain-facing
 * IAM OIDC access token server-side. `retry: false` / no refetch-storm posture
 * — same as the other ledger reads. An empty cache (`rates: []`) is a normal
 * success, never an error.
 */
export function useFxRates(enabled = true) {
  return useQuery({
    queryKey: fxRatesKey(),
    queryFn: fetchFxRates,
    enabled,
    staleTime: 30_000,
    refetchOnMount: false,
    ...READ_QUERY_REFETCH,
    retry: false,
  });
}

// --- FX 환율 history 드릴 (per-pair read; TASK-PC-FE-104) -------------------
//
// GET /api/ledger/fx-rates/{foreign}/history?limit=N (FIN-BE-040). Per-pair
// (`KRW/{foreign}`) time series, newest first. `enabled`-gated: the query only
// fires once a non-empty foreign code is supplied AND the tab is active. `limit`
// is a row count (NOT money) — clamped client-side (≤0→1, cap 500, default 50),
// mirroring the producer's own floor/cap (double-defended). `rate` is a decimal
// string (F5). An unknown / never-polled foreign code → `quotes: []` (a normal
// success, never an error).

/** Clamps the FX history `limit` (a row count, not money — F5 is amount-only):
 *  absent / non-finite → default 50; `≤ 0` → 1; `> 500` → 500. */
const clampFxHistoryLimit = (limit?: number): number => {
  if (limit === undefined || !Number.isFinite(limit)) {
    return FX_HISTORY_DEFAULT_LIMIT;
  }
  const n = Math.floor(limit);
  if (n <= 0) return 1;
  return Math.min(FX_HISTORY_MAX_LIMIT, n);
};

export function fxRateHistoryKey(foreign: string | null, limit: number) {
  return [LEDGER_KEY, 'fx-rate-history', foreign ?? '', limit] as const;
}

async function fetchFxRateHistory(
  foreign: string,
  limit: number,
): Promise<FxRateHistoryResponse> {
  const qs = new URLSearchParams();
  qs.set('limit', String(limit));
  const raw = await apiClient.get<unknown>(
    `/api/ledger/fx-rates/${encodeURIComponent(foreign)}/history?${qs.toString()}`,
  );
  return FxRateHistoryResponseSchema.parse(raw);
}

/**
 * `useFxRateHistory` — reads the per-pair FX rate history time series. READ-ONLY.
 * `enabled`-gated: the query only fires once BOTH a non-empty foreign code is
 * supplied AND `enabled` is true (the tab is active / a pair was selected). The
 * same-origin proxy attaches the domain-facing IAM OIDC access token server-side
 * — NEVER the operator token (§ 2.4.7.1 reuse). `limit` is clamped client-side.
 * `retry: false` / no refetch-storm posture, same as the other ledger reads. An
 * unknown / never-polled foreign code (`quotes: []`) is a normal success.
 */
export function useFxRateHistory(
  foreign: string | null,
  limit: number = FX_HISTORY_DEFAULT_LIMIT,
  enabled = true,
) {
  const clamped = clampFxHistoryLimit(limit);
  return useQuery({
    queryKey: fxRateHistoryKey(foreign, clamped),
    queryFn: () => fetchFxRateHistory(foreign as string, clamped),
    enabled: enabled && Boolean(foreign && foreign.trim()),
    staleTime: 30_000,
    refetchOnMount: false,
    ...READ_QUERY_REFETCH,
    retry: false,
  });
}

// --- FX 환율 수동 refresh (operator mutation; TASK-MONO-300) ----------------
//
// POST /api/ledger/fx-rates/refresh → the same-origin proxy. No request body
// (the refresh is unconditional). `retry: false` — a failure surfaces
// immediately (no client retry storm; same posture as useResolveDiscrepancy).
// `onSuccess` invalidates the fx-rates query so the table re-fetches and
// reflects the newly-upserted rates.
//
// Feed-disabled: the proxy returns 200 `{feedEnabled:false, refreshed:0}` —
// the mutation succeeds; `onSuccess` still re-fetches the list (which will
// again show `rates:[]`, consistent). The button disable-while-in-flight
// prevents double-POSTs (idempotent upserts make concurrent calls safe, but
// disabling is courteous to the external FX provider).

/**
 * `useRefreshFxRates` — triggers the FX feed on-demand refresh mutation.
 * `onSuccess` invalidates the `fxRatesKey()` query so the table re-fetches.
 * The `FxRatesRefreshResponse` result (`{feedEnabled, refreshed}`) is returned
 * to the caller so the UI can surface it (e.g. a toast or status message).
 */
export function useRefreshFxRates() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (): Promise<FxRatesRefreshResponse> => {
      const raw = await apiClient.post<unknown>('/api/ledger/fx-rates/refresh');
      return FxRatesRefreshResponseSchema.parse(raw);
    },
    retry: false,
    onSuccess: () => {
      // Re-fetch the feed cache so the table + staleness indicators update.
      qc.invalidateQueries({ queryKey: fxRatesKey() });
    },
  });
}
