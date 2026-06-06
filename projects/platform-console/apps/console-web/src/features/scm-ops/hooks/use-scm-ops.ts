'use client';

import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import {
  PoPageSchema,
  type PoPage,
  type PoQueryParams,
  SkuBreakdownSchema,
  type SkuBreakdown,
  SCM_DEFAULT_PAGE_SIZE,
  SCM_MAX_PAGE_SIZE,
} from '../api/types';

/**
 * Client-side scm-ops hooks (architecture.md § Server vs Client
 * Components — React Query is client-only). Every call goes to the
 * same-origin `/api/scm/**` proxy (the typed API client's single backend
 * entry point); the proxy attaches the HttpOnly **IAM OIDC access token**
 * server-side — the browser never reads a token or calls scm directly
 * (contract § 2.3). The § 2.4.5 per-domain credential rule is reused, NOT
 * re-derived (§ 2.4.6).
 *
 * READ-ONLY: there are NO mutation hooks at all (scm has no operator
 * mutation surface at v1). The hooks below are pure reads.
 *
 * Rate-limit discipline (§ 2.4.6 / task Edge Case "429"): the scm gateway
 * is rate-limited. There is NO tight auto-refetch loop / refetchInterval /
 * refetchOnWindowFocus — a re-query is a filter/page change (a new
 * queryKey) or an explicit user retry. The server proxy already honours a
 * 429 `Retry-After` with ONE bounded backoff; the client does NOT add its
 * own retry (React Query `retry: false` is set at the provider in tests;
 * here the queries also pin `retry: false` to never storm the gateway).
 */

const SCM_KEY = 'scm-ops';

function clampSize(size?: number): number {
  return Math.min(
    SCM_MAX_PAGE_SIZE,
    Math.max(1, size ?? SCM_DEFAULT_PAGE_SIZE),
  );
}

// --- procurement PO list read --------------------------------------------

export function poListKey(params: PoQueryParams) {
  return [
    SCM_KEY,
    'po',
    params.status ?? null,
    params.supplierId ?? null,
    Math.max(0, params.page ?? 0),
    clampSize(params.size),
  ] as const;
}

export function buildPoListQs(params: PoQueryParams): string {
  const qs = new URLSearchParams();
  if (params.status) qs.set('status', params.status);
  if (params.supplierId) qs.set('supplierId', params.supplierId);
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(clampSize(params.size)));
  return qs.toString();
}

async function fetchPoList(params: PoQueryParams): Promise<PoPage> {
  const raw = await apiClient.get<unknown>(
    `/api/scm/po?${buildPoListQs(params)}`,
  );
  return PoPageSchema.parse(raw);
}

export function useScmPoList(params: PoQueryParams, initial?: PoPage) {
  const seeded =
    initial !== undefined &&
    (params.page ?? 0) === 0 &&
    !params.status &&
    !params.supplierId;
  return useQuery({
    queryKey: poListKey(params),
    queryFn: () => fetchPoList(params),
    initialData: seeded ? initial : undefined,
    // Seeded from the server render ⇒ fresh. A filter/page change is a new
    // queryKey → one fresh proxy call. NO refetch interval / focus refetch
    // / client retry — the gateway is rate-limited (§ 2.4.6).
    staleTime: seeded ? 30_000 : 0,
    refetchOnMount: seeded ? false : true,
    refetchOnWindowFocus: false,
    refetchInterval: false,
    retry: false,
  });
}

// --- inventory-visibility per-SKU breakdown read -------------------------
//     Carries the REQUIRED S5 meta.warning (surfaced — never stripped).

export function skuKey(sku: string) {
  return [SCM_KEY, 'sku', sku] as const;
}

async function fetchSkuBreakdown(sku: string): Promise<SkuBreakdown> {
  const raw = await apiClient.get<unknown>(
    `/api/scm/sku/${encodeURIComponent(sku)}`,
  );
  return SkuBreakdownSchema.parse(raw);
}

export function useScmSkuBreakdown(sku: string | null) {
  return useQuery({
    queryKey: skuKey(sku ?? ''),
    queryFn: () => fetchSkuBreakdown(sku as string),
    enabled: Boolean(sku && sku.trim()),
    staleTime: 15_000,
    refetchOnWindowFocus: false,
    refetchInterval: false,
    retry: false,
  });
}
