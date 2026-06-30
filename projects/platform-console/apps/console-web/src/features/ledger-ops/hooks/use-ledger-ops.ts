'use client';

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
 * (As of TASK-MONO-300 the FX feed manual refresh adds a SECOND operator
 * mutation — `useRefreshFxRates` — which re-fetches the fx-rates cache
 * `onSuccess`.)
 *
 * No tight refetch loop / refetchInterval / refetchOnWindowFocus — a
 * re-query is a periodId / entryId / filter / page change (a new queryKey)
 * or an explicit user retry. **No 429 / Retry-After / backoff branch**
 * (§ 2.4.7.1 — the ledger has no documented 429; React Query
 * `retry: false` means a failure surfaces immediately, no client retry).
 *
 * ── MODULE SPLIT (TASK-PC-FE-148) ──
 * This barrel preserves the `use-ledger-ops` import path as the stable
 * public surface (`@/features/ledger-ops/hooks/use-ledger-ops`); the hooks
 * now live in cohesive sibling modules grouped by ledger sub-domain:
 *   - `use-ledger-shared.ts`         — the `LEDGER_KEY` queryKey prefix + the
 *                                      page-size clamp (feature-internal —
 *                                      NOT re-exported, both were module-
 *                                      private in the pre-split file).
 *   - `use-ledger-periods.ts`        — trial-balance + accounting-periods
 *                                      list/detail reads.
 *   - `use-ledger-entries.ts`        — journal-entry + account balance/entries
 *                                      reads.
 *   - `use-ledger-reconciliation.ts` — discrepancy queue/detail + statement
 *                                      reads + the discrepancy RESOLVE mutation.
 *   - `use-ledger-fx.ts`             — FX position lots / rate feed / rate
 *                                      history reads + the FX refresh mutation.
 * Pure structural split — 0 behavior change (every hook's name / signature /
 * queryKey / call order is verbatim from the pre-split file).
 */

export * from './use-ledger-periods';
export * from './use-ledger-entries';
export * from './use-ledger-reconciliation';
export * from './use-ledger-fx';
