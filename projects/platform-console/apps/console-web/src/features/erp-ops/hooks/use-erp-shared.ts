'use client';

import { useCallback, useMemo } from 'react';
import { usePathname, useRouter, useSearchParams } from 'next/navigation';
import { clampPageSize } from '@/shared/lib/pagination';
import {
  type ErpListQueryParams,
  type ErpDetailQueryParams,
  ERP_DEFAULT_PAGE_SIZE,
  ERP_MAX_PAGE_SIZE,
} from '../api/types';
import { normaliseAsOf } from '../api/erp-keys';

/**
 * Shared erp-ops hook infrastructure (TASK-PC-FE-099 split). Holds the
 * E3 first-class asOf machinery (`useAsOf` URL-param source of truth +
 * `useThreadedAsOf`) and the masterdata list/detail query-string builders
 * used across the masters + delegation-facts read hooks. `useAsOf` /
 * `UseAsOfResult` are the only public symbols re-exported from the
 * `use-erp-ops` barrel; the rest are feature-internal (imported by the
 * sibling hook modules, never widened to the public surface).
 */

export const clampSize = (size?: number): number =>
  clampPageSize(size, ERP_DEFAULT_PAGE_SIZE, ERP_MAX_PAGE_SIZE);

// ---------------------------------------------------------------------------
// useAsOf — URL-param-bound E3 first-class hook (the single source
// of truth for the section's effective-instant).
// ---------------------------------------------------------------------------

export interface UseAsOfResult {
  /** Current asOf (ISO-8601 DATE string) from the URL — `null`
   *  when absent (producer resolves to "today" UTC). */
  asOf: string | null;
  /** Sets / clears the asOf URL param (replaces the current
   *  history entry, preserves other query params). */
  setAsOf: (next: string | null) => void;
}

/**
 * Reads the `?asOf=` URL search-param and exposes a setter that
 * writes it back to the URL. This is the SINGLE source of truth
 * for the section's effective-instant — every list / detail hook
 * pulls from here and threads `asOf` to the producer verbatim
 * (the E3 core invariant; the asOf URL → query refetch → producer
 * pass-through chain).
 */
export function useAsOf(): UseAsOfResult {
  const router = useRouter();
  const pathname = usePathname();
  const search = useSearchParams();
  const asOf = search?.get('asOf');
  const setAsOf = useCallback(
    (next: string | null) => {
      const params = new URLSearchParams(search?.toString() ?? '');
      const norm = normaliseAsOf(next);
      if (norm) params.set('asOf', norm);
      else params.delete('asOf');
      const qs = params.toString();
      // App-router replace (no history push so the back-button
      // path stays clean); preserves other params.
      router.replace(`${pathname ?? ''}${qs ? `?${qs}` : ''}`);
    },
    [router, pathname, search],
  );
  return { asOf: asOf || null, setAsOf };
}

/**
 * Builds the `asOf=…&active=…&page=…&size=…` query string the
 * proxy forwards to the producer. CORE E3 — the asOf threads
 * through verbatim. NO Number coercion of the wire string; page /
 * size arithmetic is on the integer page-index NUMBERS only (not
 * money — there is no F5 here).
 */
export function buildListQs(params: ErpListQueryParams): string {
  const qs = new URLSearchParams();
  if (params.asOf) qs.set('asOf', params.asOf);
  if (params.active !== undefined) qs.set('active', String(params.active));
  if (params.filters) {
    for (const [k, v] of Object.entries(params.filters)) {
      if (v !== undefined && v !== null && v !== '') qs.set(k, v);
    }
  }
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(clampSize(params.size)));
  return qs.toString();
}

export function buildDetailQs(params: ErpDetailQueryParams): string {
  const qs = new URLSearchParams();
  if (params.asOf) qs.set('asOf', params.asOf);
  const s = qs.toString();
  return s ? `?${s}` : '';
}

/** Internal — picks the asOf threaded into the hook (explicit
 *  param wins over the URL hook). When the caller omits `asOf` we
 *  read from `useAsOf()` so a single asOf change re-renders every
 *  list / detail subscribed under this section. */
export function useThreadedAsOf(explicit?: string | null): string | undefined {
  const { asOf } = useAsOf();
  return useMemo(() => {
    const chosen = explicit !== undefined ? explicit : asOf;
    return normaliseAsOf(chosen);
  }, [explicit, asOf]);
}
