'use client';

import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import { RegistryResponseSchema } from '@/shared/api/registry-types';
import type { CatalogState } from '@/shared/api/registry-types';

/**
 * Client-side catalog re-query hook.
 *
 * Calls the same-origin `/api/registry` route handler (which attaches the
 * HttpOnly operator token server-side — never a direct GAP call from the
 * browser; architecture.md § Boundary Rules). Seeded from the server-rendered
 * catalog so first paint is instant; used to silently recover a `degraded`
 * state without a full reload (task Acceptance).
 */
async function queryCatalog(): Promise<CatalogState> {
  const raw = await apiClient.get<unknown>('/api/registry');
  const parsed = RegistryResponseSchema.parse(raw);
  return { products: parsed.products, degraded: false };
}

export function useCatalog(initial: CatalogState) {
  return useQuery({
    queryKey: ['catalog'],
    queryFn: queryCatalog,
    initialData: initial,
    // If the server render was degraded, refetch promptly to recover.
    refetchInterval: initial.degraded ? 15_000 : false,
  });
}
