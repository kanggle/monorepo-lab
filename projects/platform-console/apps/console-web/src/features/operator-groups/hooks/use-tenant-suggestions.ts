'use client';

import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import { RegistryResponseSchema } from '@/shared/api/registry-types';
import { READ_QUERY_REFETCH } from '@/shared/api/query-options';
import { registryTenantSuggestions } from '@/shared/lib/registry-tenant-suggestions';

/**
 * Lazily-loaded tenant-id suggestions for a free-text input's `<datalist>`
 * (TASK-PC-FE-336 — `operator-groups` `GroupForm`/`GroupGrantDialog` tenant
 * fields). Generic over any free-text tenant-id input, so it lives in
 * `shared/` rather than inside one feature.
 *
 * Fetches the SAME `/api/registry` source as the top tenant switcher — but
 * only on first {@link TenantSuggestions.load} call (typically the input's
 * `onFocus`), NOT on mount: `GroupForm`'s create-mode tenant field renders
 * unconditionally on screen mount, so an eager fetch here would fire a GET the
 * operator never asked for. `load()` is idempotent and the query is keyed
 * globally (`TENANT_SUGGESTIONS_KEY`), so N tenant fields on one screen share
 * ONE GET.
 *
 * Failure (`isError`, incl. a schema-parse failure) ⇒ `options` stays `[]` —
 * the input keeps working as a plain free-text field with no suggestions
 * (task AC-4 / Failure Scenario "레지스트리 503·401 → 제안 없이 기존 입력").
 */

const TENANT_SUGGESTIONS_KEY = ['registry', 'tenant-suggestions'] as const;

async function fetchTenantSuggestions(): Promise<string[]> {
  const raw = await apiClient.get<unknown>('/api/registry');
  const parsed = RegistryResponseSchema.parse(raw);
  return registryTenantSuggestions(parsed.products);
}

export interface TenantSuggestions {
  /** Known tenant ids (sorted, `'*'` excluded); `[]` while loading or on failure. */
  options: string[];
  /** Triggers the (at-most-once) registry fetch. Call from e.g. `onFocus`. */
  load: () => void;
}

export function useTenantSuggestions(): TenantSuggestions {
  const [enabled, setEnabled] = useState(false);
  const q = useQuery({
    queryKey: TENANT_SUGGESTIONS_KEY,
    queryFn: fetchTenantSuggestions,
    enabled,
    retry: false,
    staleTime: 60_000,
    ...READ_QUERY_REFETCH,
  });
  return {
    options: q.data ?? [],
    load: () => setEnabled(true),
  };
}
