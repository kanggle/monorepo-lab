'use client';

import {
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import {
  ReorderPolicySchema,
  type ReorderPolicy,
  type ReorderPolicyInput,
  SupplierMapSchema,
  type SupplierMap,
  type SupplierMapInput,
  type SeedLookup,
} from '../api/types';

/**
 * Client-side scm-config hooks (architecture.md § Server vs Client Components —
 * React Query is client-only). Every call goes to the same-origin
 * `/api/scm/demand-planning/{policies,sku-supplier-map}/[skuCode]` proxy; the
 * proxy attaches the HttpOnly **domain-facing IAM OIDC token** server-side
 * (§ 2.4.6.2 reusing the § 2.4.6 per-domain credential rule). The browser never
 * reads a token or calls scm directly.
 *
 * Read queries are **SKU-code-driven** — `enabled` only once a non-empty SKU
 * code is entered (no list route exists; nothing is fetched until the operator
 * looks up a SKU). The proxy returns a typed `{ found: false }` for a producer
 * `404` (not configured yet) — NOT an error — so the read query resolves
 * SUCCESSFULLY to a not-found state, never an error toast.
 *
 * Mutation discipline (§ 2.4.6.2): PUT is an idempotent upsert — the body IS
 * the FULL row. NO `Idempotency-Key`, NO `X-Operator-Reason` (the producer
 * defines neither). On success the corresponding read is invalidated so the
 * upserted row reflects without a manual reload.
 *
 * Rate-limit discipline (§ 2.4.6 reuse): NO refetch loop / focus refetch;
 * `retry: false` — the gateway is rate-limited and the server proxy already
 * honours a 429 `Retry-After` with ONE bounded backoff.
 */

const CFG_KEY = 'scm-config';

// --- query keys ----------------------------------------------------------

export function policyKey(skuCode: string) {
  return [CFG_KEY, 'policy', skuCode] as const;
}
export function supplierMapKey(skuCode: string) {
  return [CFG_KEY, 'sku-supplier-map', skuCode] as const;
}

// --- the typed seed-lookup envelope on the wire --------------------------
//   The proxy returns `{ found: true, value }` (200 row) or `{ found: false }`
//   (producer 404 — not configured yet). Parsed tolerantly.

function parsePolicyLookup(raw: unknown): SeedLookup<ReorderPolicy> {
  const env = (raw ?? {}) as { found?: unknown; value?: unknown };
  if (env.found === false) return { found: false };
  return { found: true, value: ReorderPolicySchema.parse(env.value ?? raw) };
}

function parseSupplierMapLookup(raw: unknown): SeedLookup<SupplierMap> {
  const env = (raw ?? {}) as { found?: unknown; value?: unknown };
  if (env.found === false) return { found: false };
  return { found: true, value: SupplierMapSchema.parse(env.value ?? raw) };
}

// --- reorder policy: read + upsert ---------------------------------------

async function fetchPolicy(
  skuCode: string,
): Promise<SeedLookup<ReorderPolicy>> {
  const raw = await apiClient.get<unknown>(
    `/api/scm/demand-planning/policies/${encodeURIComponent(skuCode)}`,
  );
  return parsePolicyLookup(raw);
}

export function usePolicy(skuCode: string) {
  const sku = skuCode.trim();
  return useQuery({
    queryKey: policyKey(sku),
    queryFn: () => fetchPolicy(sku),
    enabled: sku.length > 0,
    staleTime: 0,
    refetchOnWindowFocus: false,
    refetchInterval: false,
    retry: false,
  });
}

export interface UpsertPolicyArgs {
  skuCode: string;
  body: ReorderPolicyInput;
}

export function useUpsertPolicy() {
  const qc = useQueryClient();
  return useMutation<ReorderPolicy, unknown, UpsertPolicyArgs>({
    mutationFn: async ({ skuCode, body }) => {
      // The body IS the full row (idempotent upsert). NO Idempotency-Key, NO
      // X-Operator-Reason — apiClient.put attaches neither.
      const raw = await apiClient.put<unknown>(
        `/api/scm/demand-planning/policies/${encodeURIComponent(skuCode)}`,
        body,
      );
      return ReorderPolicySchema.parse(raw);
    },
    onSuccess: (_data, { skuCode }) => {
      // The "not configured → configured" transition must reflect without a
      // manual reload — invalidate THIS SKU's policy read.
      qc.invalidateQueries({ queryKey: policyKey(skuCode.trim()) });
    },
  });
}

// --- sku→supplier mapping: read + upsert ---------------------------------

async function fetchSupplierMap(
  skuCode: string,
): Promise<SeedLookup<SupplierMap>> {
  const raw = await apiClient.get<unknown>(
    `/api/scm/demand-planning/sku-supplier-map/${encodeURIComponent(skuCode)}`,
  );
  return parseSupplierMapLookup(raw);
}

export function useSupplierMap(skuCode: string) {
  const sku = skuCode.trim();
  return useQuery({
    queryKey: supplierMapKey(sku),
    queryFn: () => fetchSupplierMap(sku),
    enabled: sku.length > 0,
    staleTime: 0,
    refetchOnWindowFocus: false,
    refetchInterval: false,
    retry: false,
  });
}

export interface UpsertSupplierMapArgs {
  skuCode: string;
  body: SupplierMapInput;
}

export function useUpsertSupplierMap() {
  const qc = useQueryClient();
  return useMutation<SupplierMap, unknown, UpsertSupplierMapArgs>({
    mutationFn: async ({ skuCode, body }) => {
      const raw = await apiClient.put<unknown>(
        `/api/scm/demand-planning/sku-supplier-map/${encodeURIComponent(skuCode)}`,
        body,
      );
      return SupplierMapSchema.parse(raw);
    },
    onSuccess: (_data, { skuCode }) => {
      qc.invalidateQueries({ queryKey: supplierMapKey(skuCode.trim()) });
    },
  });
}
