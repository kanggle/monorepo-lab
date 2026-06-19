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
  TrialBalanceSchema,
  type TrialBalance,
  PeriodSchema,
  type Period,
  PeriodsResponseSchema,
  type PeriodsResponse,
  type PeriodsQueryParams,
  JournalEntrySchema,
  type JournalEntry,
  DiscrepancySchema,
  type Discrepancy,
  DiscrepanciesResponseSchema,
  type DiscrepanciesResponse,
  type DiscrepanciesQueryParams,
  type ResolveDiscrepancyBody,
  AccountBalanceSchema,
  type AccountBalance,
  AccountEntriesResponseSchema,
  type AccountEntriesResponse,
  type AccountEntriesQueryParams,
  StatementSchema,
  type Statement,
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
  LEDGER_DEFAULT_PAGE_SIZE,
  LEDGER_MAX_PAGE_SIZE,
} from '../api/types';

/**
 * Client-side finance ledger-ops hooks (architecture.md § Server vs Client
 * Components — React Query is client-only). Every call goes to the
 * same-origin `/api/ledger/**` proxy (the typed API client's single backend
 * entry point); the proxy attaches the HttpOnly **IAM OIDC access token**
 * server-side — the browser never reads a token or calls the ledger
 * directly (contract § 2.3). The § 2.4.5 per-domain credential rule is
 * reused via the § 2.4.7 finance binding, NOT re-derived (§ 2.4.7.1).
 *
 * READ + ONE MUTATION: the read hooks below are pure reads. As of
 * TASK-PC-FE-073 there is EXACTLY ONE mutation hook — `useResolveDiscrepancy`
 * (the reconciliation discrepancy *resolve*) — which POSTs to the same-origin
 * proxy with a body `{ resolutionType, note }` (NO `Idempotency-Key` — the
 * producer defines none; the `409 RECONCILIATION_ALREADY_RESOLVED` state
 * guard is the double-submit defence) and, `onSuccess`, invalidates the
 * discrepancy list + detail queries so the queue/detail reflect `RESOLVED`.
 *
 * No tight refetch loop / refetchInterval / refetchOnWindowFocus — a
 * re-query is a periodId / entryId / filter / page change (a new queryKey)
 * or an explicit user retry. **No 429 / Retry-After / backoff branch**
 * (§ 2.4.7.1 — the ledger has no documented 429; React Query
 * `retry: false` means a failure surfaces immediately, no client retry).
 */

const LEDGER_KEY = 'ledger-ops';

// Page size arithmetic — NOT money. F5 invariant is amount-only.
const clampSize = (size?: number): number =>
  clampPageSize(size, LEDGER_DEFAULT_PAGE_SIZE, LEDGER_MAX_PAGE_SIZE);

// --- trial balance read --------------------------------------------------

export function trialBalanceKey() {
  return [LEDGER_KEY, 'trial-balance'] as const;
}

async function fetchTrialBalance(): Promise<TrialBalance> {
  const raw = await apiClient.get<unknown>('/api/ledger/trial-balance');
  return TrialBalanceSchema.parse(raw);
}

export function useTrialBalance(initial?: TrialBalance) {
  return useQuery({
    queryKey: trialBalanceKey(),
    queryFn: fetchTrialBalance,
    initialData: initial,
    staleTime: 30_000,
    refetchOnMount: false,
    ...READ_QUERY_REFETCH,
    retry: false,
  });
}

// --- periods list read ---------------------------------------------------

export function periodsKey(params: PeriodsQueryParams) {
  return [
    LEDGER_KEY,
    'periods',
    Math.max(0, params.page ?? 0),
    clampSize(params.size),
  ] as const;
}

export function buildPeriodsQs(params: PeriodsQueryParams): string {
  const qs = new URLSearchParams();
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(clampSize(params.size)));
  return qs.toString();
}

async function fetchPeriods(
  params: PeriodsQueryParams,
): Promise<PeriodsResponse> {
  const raw = await apiClient.get<unknown>(
    `/api/ledger/periods?${buildPeriodsQs(params)}`,
  );
  return PeriodsResponseSchema.parse(raw);
}

export function usePeriods(
  params: PeriodsQueryParams,
  initial?: PeriodsResponse,
) {
  const seeded = initial !== undefined && (params.page ?? 0) === 0;
  return useQuery({
    queryKey: periodsKey(params),
    queryFn: () => fetchPeriods(params),
    initialData: seeded ? initial : undefined,
    staleTime: seeded ? 30_000 : 0,
    refetchOnMount: seeded ? false : true,
    ...READ_QUERY_REFETCH,
    retry: false,
  });
}

// --- period detail read --------------------------------------------------

export function periodKey(periodId: string | null) {
  return [LEDGER_KEY, 'period', periodId ?? ''] as const;
}

async function fetchPeriod(periodId: string): Promise<Period> {
  const raw = await apiClient.get<unknown>(
    `/api/ledger/periods/${encodeURIComponent(periodId)}`,
  );
  return PeriodSchema.parse(raw);
}

export function usePeriod(periodId: string | null) {
  return useQuery({
    queryKey: periodKey(periodId),
    queryFn: () => fetchPeriod(periodId as string),
    enabled: Boolean(periodId && periodId.trim()),
    staleTime: 30_000,
    refetchOnMount: false,
    ...READ_QUERY_REFETCH,
    retry: false,
  });
}

// --- journal entry read (id-driven; no list/search) ----------------------

export function journalEntryKey(entryId: string | null) {
  return [LEDGER_KEY, 'entry', entryId ?? ''] as const;
}

async function fetchJournalEntry(entryId: string): Promise<JournalEntry> {
  const raw = await apiClient.get<unknown>(
    `/api/ledger/entries/${encodeURIComponent(entryId)}`,
  );
  return JournalEntrySchema.parse(raw);
}

export function useJournalEntry(
  entryId: string | null,
  initial?: JournalEntry,
) {
  const seeded = initial !== undefined && Boolean(entryId);
  return useQuery({
    queryKey: journalEntryKey(entryId),
    queryFn: () => fetchJournalEntry(entryId as string),
    enabled: Boolean(entryId && entryId.trim()),
    initialData: seeded ? initial : undefined,
    staleTime: 30_000,
    refetchOnMount: false,
    ...READ_QUERY_REFETCH,
    retry: false,
  });
}

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

// --- account balance read (id-driven; TASK-PC-FE-074) ---------------------

export function accountBalanceKey(code: string | null) {
  return [LEDGER_KEY, 'account-balance', code ?? ''] as const;
}

async function fetchAccountBalance(code: string): Promise<AccountBalance> {
  const raw = await apiClient.get<unknown>(
    `/api/ledger/accounts/${encodeURIComponent(code)}/balance`,
  );
  return AccountBalanceSchema.parse(raw);
}

/**
 * `useAccountBalance` — reads the running balance for a ledger account.
 * READ-ONLY. The same-origin proxy attaches the domain-facing IAM OIDC
 * access token server-side — NEVER the operator token (§ 2.4.7.1 reuse).
 * `retry: false` / no-refetch-storm posture, same as the other ledger reads.
 * `initialData` is used when the server-seeded `initial` matches the
 * requested `code` (to avoid a double-fetch on SSR→CSR transition).
 */
export function useAccountBalance(
  code: string | null,
  initial?: AccountBalance,
) {
  const seeded = initial !== undefined && Boolean(code);
  return useQuery({
    queryKey: accountBalanceKey(code),
    queryFn: () => fetchAccountBalance(code as string),
    enabled: Boolean(code && code.trim()),
    initialData: seeded ? initial : undefined,
    staleTime: 30_000,
    refetchOnMount: false,
    ...READ_QUERY_REFETCH,
    retry: false,
  });
}

// --- account entries read (paginated; TASK-PC-FE-074) ---------------------

export function accountEntriesKey(
  code: string | null,
  params: AccountEntriesQueryParams,
) {
  return [
    LEDGER_KEY,
    'account-entries',
    code ?? '',
    Math.max(0, params.page ?? 0),
    clampSize(params.size),
  ] as const;
}

export function buildAccountEntriesQs(params: AccountEntriesQueryParams): string {
  const qs = new URLSearchParams();
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(clampSize(params.size)));
  return qs.toString();
}

async function fetchAccountEntries(
  code: string,
  params: AccountEntriesQueryParams,
): Promise<AccountEntriesResponse> {
  const raw = await apiClient.get<unknown>(
    `/api/ledger/accounts/${encodeURIComponent(code)}/entries?${buildAccountEntriesQs(params)}`,
  );
  return AccountEntriesResponseSchema.parse(raw);
}

/**
 * `useAccountEntries` — reads the paginated journal lines posted to one
 * account. READ-ONLY. Same posture as `usePeriods` / `useDiscrepancies`.
 * `initialData` is used when the server-seeded `initial` matches page 0.
 */
export function useAccountEntries(
  code: string | null,
  params: AccountEntriesQueryParams,
  initial?: AccountEntriesResponse,
) {
  const seeded = initial !== undefined && (params.page ?? 0) === 0;
  return useQuery({
    queryKey: accountEntriesKey(code, params),
    queryFn: () => fetchAccountEntries(code as string, params),
    enabled: Boolean(code && code.trim()),
    initialData: seeded ? initial : undefined,
    staleTime: seeded ? 30_000 : 0,
    refetchOnMount: seeded ? false : true,
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
