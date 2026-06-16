'use client';

/**
 * Client-side erp-ops hooks (architecture.md § Server vs Client
 * Components — React Query is client-only). Every call goes to the
 * same-origin `/api/erp/**` proxy (the typed API client's single
 * backend entry point); the proxy attaches the HttpOnly **IAM OIDC
 * access token** server-side — the browser never reads a token or
 * calls erp directly (contract § 2.3). The § 2.4.5 per-domain
 * credential rule is reused, NOT re-derived (§ 2.4.8).
 *
 * READ + DEPARTMENT WRITE PILOT: the list/detail hooks are pure
 * reads. The department master additionally has four mutation hooks
 * (TASK-PC-FE-046 — `useCreateDepartment` / `useUpdateDepartment` /
 * `useRetireDepartment` / `useMoveDepartmentParent`; § 2.4.8
 * *Department write binding (PILOT)*). The OTHER FOUR masters have NO
 * mutation hook (a test pins that absence). Department writes go to a
 * same-origin POST proxy that forwards with the correct upstream
 * method; on success the department queries are invalidated by prefix.
 *
 * E3 FIRST-CLASS ASOF (§ 2.4.8 / `<AsOfPicker>` thread-through):
 * `useAsOf()` reads the `?asOf=` URL search-param (it is the SINGLE
 * source of truth — no separate component state to drift); list
 * and detail hooks pull from `useAsOf()` and inject it into the
 * proxy URL so the producer receives `asOf=<value>` verbatim. The
 * queryKey is bound to the asOf so React Query refetches on
 * change. NO tight refetch loop / `refetchInterval` /
 * `refetchOnWindowFocus`. **No 429 / Retry-After / backoff branch**
 * (§ 2.4.8 — erp has no documented 429; React Query `retry: false`
 * means an erp failure surfaces immediately, no client retry).
 *
 * ── MODULE SPLIT (TASK-PC-FE-099) ──
 * This barrel preserves the `use-erp-ops` import path as the stable
 * public surface (`@/features/erp-ops/hooks/use-erp-ops`); the hooks
 * now live in cohesive sibling modules:
 *   - `use-erp-shared.ts`     — E3 asOf machinery (`useAsOf` +
 *                               `useThreadedAsOf`) + list/detail qs
 *                               builders + the page-size clamp.
 *   - `use-erp-masters.ts`    — the 5 masters' read + write hooks +
 *                               the read-model employee org-view read.
 *   - `use-erp-approval.ts`   — approval-workflow read + mutation hooks.
 *   - `use-erp-delegation.ts` — delegation grant hooks + the read-model
 *                               delegation-fact reads.
 * Only `useAsOf` / `UseAsOfResult` are re-exported from `use-erp-shared`
 * (the qs/clamp/threading helpers stay feature-internal — not part of
 * the public surface). Pure structural split — 0 behavior change.
 */

export { useAsOf, type UseAsOfResult } from './use-erp-shared';
export * from './use-erp-masters';
export * from './use-erp-approval';
export * from './use-erp-delegation';
