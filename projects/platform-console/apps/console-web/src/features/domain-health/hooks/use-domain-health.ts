'use client';

import { useQuery } from '@tanstack/react-query';
import { fetchDomainHealth } from '../api/domain-health-api';
import type { DomainHealth } from '../api/types';

/**
 * Client-side React Query hook (TASK-PC-FE-013) for the domain-health
 * explicit user retry.
 *
 * Server-component first by design (architecture.md § Server vs Client
 * Components): the initial envelope is composed server-side by the
 * route entry (`page.tsx`) via `fetchDomainHealth()` and rendered by
 * the server `<DomainHealthScreen>`. This hook is consumed ONLY by
 * `<RetryButton>` — the page passes the SSR initial as `initialData`,
 * the button's `onClick` calls `refetch()`.
 *
 * Hard invariants (§ 2.4.4 / § 2.4.9 / TASK-PC-FE-013 AC):
 *   - NO `refetchInterval` (no auto-poll into the 5 producers).
 *   - NO `refetchOnWindowFocus` (no refetch storm on a tab regain).
 *   - NO `refetchOnReconnect: 'always'`.
 *   - Default `retry: false` on failures (the operator decides what
 *     to do — re-click the retry button).
 */

const DOMAIN_HEALTH_KEY = ['domain-health'] as const;

export function useDomainHealth(initial?: DomainHealth) {
  return useQuery({
    queryKey: DOMAIN_HEALTH_KEY,
    queryFn: fetchDomainHealth,
    initialData: initial,
    staleTime: 30_000,
    refetchOnMount: initial ? false : true,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
    retry: false,
  });
}
