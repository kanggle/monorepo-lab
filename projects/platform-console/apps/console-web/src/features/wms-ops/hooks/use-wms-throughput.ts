'use client';

import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import { READ_QUERY_REFETCH } from '@/shared/api/query-options';
import { ThroughputSchema, type Throughput } from '../api/types';

/**
 * TASK-PC-FE-358 — the `/wms` 개요 throughput card's warehouse-switch re-query
 * through the same-origin `/api/wms/throughput` proxy (contract § 2.4.5
 * routing note). Kept in its own module (not `use-wms-ops.ts`) so the card's
 * client surface stays self-contained.
 *
 * `enabled` only for a warehouse OTHER than the server-rendered default — the
 * default's window arrived with the SSR state, so it is never fetched twice.
 * No refetch interval / window-focus (§ 2.4.5 — the read model's lag is
 * surfaced, not polled-around); `retry: false` so a failed switch shows its
 * notice at once instead of stalling on retries.
 */

export function throughputKey(warehouseId: string | null, from: string, to: string) {
  return ['wms-ops', 'throughput', warehouseId, from, to] as const;
}

export function buildThroughputQs(args: {
  warehouseId: string;
  from: string;
  to: string;
}): string {
  return new URLSearchParams({
    warehouseId: args.warehouseId,
    from: args.from,
    to: args.to,
  }).toString();
}

async function fetchThroughput(args: {
  warehouseId: string;
  from: string;
  to: string;
}): Promise<Throughput> {
  const raw = await apiClient.get<unknown>(
    `/api/wms/throughput?${buildThroughputQs(args)}`,
  );
  return ThroughputSchema.parse(raw);
}

export function useWmsThroughput(args: {
  warehouseId: string | null;
  from: string;
  to: string;
  enabled: boolean;
}) {
  return useQuery({
    queryKey: throughputKey(args.warehouseId, args.from, args.to),
    queryFn: () =>
      fetchThroughput({
        warehouseId: args.warehouseId as string,
        from: args.from,
        to: args.to,
      }),
    enabled: args.enabled && args.warehouseId !== null,
    retry: false,
    ...READ_QUERY_REFETCH,
  });
}
