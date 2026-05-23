'use client';

import { useQuery } from '@tanstack/react-query';
import { fetchOperatorOverview } from '../api/operator-overview-api';
import type { OperatorOverview } from '../api/operator-overview-types';

/**
 * Client-side React Query hook (TASK-PC-FE-011) for the operator
 * overview's explicit user retry.
 *
 * Server-component first by design (architecture.md § Server vs Client
 * Components): the initial overview is composed server-side by the
 * route entry (`page.tsx`) via `fetchOperatorOverview()` and rendered
 * by the server `<OperatorOverviewScreen>`. This hook is consumed
 * ONLY by `<RetryButton>` — the page passes the SSR initial as
 * `initialData`, the button's `onClick` calls `refetch()`.
 *
 * Hard invariants (§ 2.4.4 / § 2.4.9 / TASK-PC-FE-011 AC):
 *   - NO `refetchInterval` (no auto-poll into the 5 producers).
 *   - NO `refetchOnWindowFocus` (no refetch storm on a tab regain).
 *   - NO `refetchOnReconnect: 'always'`.
 *   - Default `retry: false` on failures (the operator decides what
 *     to do — re-click the retry button).
 * Only `<RetryButton>` (client component) triggers a refetch by
 * calling `query.refetch()` explicitly.
 */

const OPERATOR_OVERVIEW_KEY = ['operator-overview'] as const;

export function useOperatorOverview(initial?: OperatorOverview) {
  return useQuery({
    queryKey: OPERATOR_OVERVIEW_KEY,
    // TASK-PC-FE-030 — `fetchOperatorOverview` now takes an optional
    // server-only `cookieHeader`. React Query passes its context object
    // to `queryFn`; wrap so the client path calls with no args (the
    // browser supplies cookies natively via `credentials: 'include'`).
    queryFn: () => fetchOperatorOverview(),
    initialData: initial,
    // Seeded from the SSR render ⇒ fresh; the operator decides when
    // to re-fetch via the explicit retry button. The audit-respecting
    // bounded-fan-out invariant of § 2.4.9 forbids a refetch storm.
    staleTime: 30_000,
    refetchOnMount: initial ? false : true,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
    retry: false,
  });
}
