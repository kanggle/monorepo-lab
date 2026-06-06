'use client';

import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import { OperatorOverviewSchema, type OperatorOverview } from '../api/types';

/**
 * Client-side composed-overview read hook (architecture.md § Server vs
 * Client Components — React Query is client-only). The call goes to the
 * same-origin `/api/dashboards` proxy (the typed API client's single
 * backend entry point); the proxy composes the bounded fan-out server-side
 * with the HttpOnly operator token + active tenant — the browser never
 * reads a token or calls IAM directly (contract § 2.3 / § 2.4.4).
 *
 * READ-ONLY + PRODUCER-META-AUDIT-RESPECTING (§ 2.4.4 / audit-heavy A5):
 * the audit leg of the fan-out is meta-audited producer-side. There is a
 * SINGLE bounded query and NO aggressive auto-refetch — one overview load
 * = one bounded set of calls. `refetchOnWindowFocus` / `refetchInterval`
 * are OFF (a refetch storm would flood the producer meta-audit). A
 * degraded re-query is an explicit user retry (`refetch()`), never an
 * automatic interval.
 *
 * Per-source isolation lives server-side in `getOperatorOverview()`: a
 * degraded/forbidden CARD is part of the 200 payload (a card status, not
 * a query error). A query ERROR here means the WHOLE overview failed —
 * a 401 maps (via the api client) to a forced re-login (no partial
 * authed state); the proxy maps a non-401 whole-fan-out failure to 503.
 */

const OVERVIEW_KEY = ['operator-overview'] as const;

async function fetchOverview(): Promise<OperatorOverview> {
  const raw = await apiClient.get<unknown>('/api/dashboards');
  return OperatorOverviewSchema.parse(raw);
}

export function useOperatorOverview(initial?: OperatorOverview) {
  return useQuery({
    queryKey: OVERVIEW_KEY,
    queryFn: fetchOverview,
    initialData: initial,
    // Seeded from the server render ⇒ fresh (the server already composed
    // it with the operator token). NO refetch interval / NO
    // refetchOnWindowFocus — the audit leg is producer meta-audited; one
    // load = one bounded call set. A retry is an explicit user action.
    staleTime: initial ? 30_000 : 0,
    refetchOnMount: initial ? false : true,
    refetchOnWindowFocus: false,
    refetchInterval: false,
  });
}
