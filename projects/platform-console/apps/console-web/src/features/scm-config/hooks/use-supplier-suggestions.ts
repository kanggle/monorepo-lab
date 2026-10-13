'use client';

import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import { READ_QUERY_REFETCH } from '@/shared/api/query-options';
import {
  SupplierPageSchema,
  SCM_SUPPLIER_MAX_PAGE_SIZE,
} from '@/shared/api/scm-suppliers-types';
import type { SeedTextFieldOption } from '../components/SeedTextField';

/**
 * Lazily-loaded supplier CODE suggestions for `SupplierMapForm`'s
 * `supplierId` `<datalist>` (TASK-PC-FE-337). `sku-supplier-map.supplierId`
 * is FREE-TEXT and must stay a code the server never validates (a code not
 * in the master list is still contract-valid — ADR-MONO-027 D3, task Scope);
 * this only offers a convenience pick FROM the real procurement supplier
 * master (ADR-MONO-050 D9 / TASK-MONO-677) so the operator does not have to
 * already know the code by heart. The inserted `<option value>` is always the
 * supplier's **code** — never the id — the label additionally shows name +
 * status so the operator can tell suppliers apart ({@link SeedTextFieldOption}).
 *
 * Mirrors `features/operator-groups/hooks/use-tenant-suggestions.ts`'s shape
 * exactly: fetches through the same-origin proxy ONLY on the first
 * {@link SupplierSuggestions.load} call (the field's `onFocus`), not on
 * mount — `SupplierMapForm` renders unconditionally, so an eager fetch would
 * be an unrequested GET. `load()` is idempotent; the query is keyed
 * globally, so re-focusing after the first load does not re-fetch within
 * `staleTime`.
 *
 * Failure (`isError`, incl. a schema-parse failure) ⇒ `options` stays `[]` —
 * the field keeps working as a plain free-text input with no suggestions
 * (task AC-3 / Failure Scenario "공급사 목록 503·403·401 → 직접 입력으로 대체").
 */

const SUPPLIER_SUGGESTIONS_KEY = ['scm-config', 'supplier-suggestions'] as const;

function supplierSuggestionLabel(s: {
  code: string;
  name: string;
  status?: string;
}): string {
  return `${s.code} · ${s.name} · ${s.status ?? '—'}`;
}

async function fetchSupplierOptions(): Promise<SeedTextFieldOption[]> {
  const raw = await apiClient.get<unknown>(
    `/api/scm/procurement/suppliers?page=0&size=${SCM_SUPPLIER_MAX_PAGE_SIZE}`,
  );
  const page = SupplierPageSchema.parse(raw);
  return page.content.map((s) => ({
    value: s.code,
    label: supplierSuggestionLabel(s),
  }));
}

export interface SupplierSuggestions {
  /** `{value: code, label}` pairs for a `SeedTextField` `options` prop;
   *  `[]` while loading or on failure. */
  options: SeedTextFieldOption[];
  /** Triggers the (at-most-once) supplier-list fetch. Call from `onFocus`. */
  load: () => void;
}

export function useSupplierSuggestions(): SupplierSuggestions {
  const [enabled, setEnabled] = useState(false);
  const q = useQuery({
    queryKey: SUPPLIER_SUGGESTIONS_KEY,
    queryFn: fetchSupplierOptions,
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
