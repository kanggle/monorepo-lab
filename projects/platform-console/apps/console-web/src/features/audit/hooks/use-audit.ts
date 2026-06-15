'use client';

import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import { READ_QUERY_REFETCH } from '@/shared/api/query-options';
import {
  AuditPageSchema,
  type AuditPage,
  type AuditQueryParams,
  AUDIT_MAX_PAGE_SIZE,
  AUDIT_DEFAULT_PAGE_SIZE,
} from '../api/types';

/**
 * Client-side audit read hook (architecture.md § Server vs Client
 * Components — React Query is client-only). The call goes to the
 * same-origin `/api/audit` proxy (the typed API client's single backend
 * entry point); the proxy attaches the HttpOnly operator token + tenant
 * server-side — the browser never reads a token or calls IAM directly
 * (contract § 2.3).
 *
 * READ-ONLY (console-integration-contract § 2.4.2 / audit-heavy A5): no
 * mutation; keyed by the FULL filter set so each distinct query is one
 * call. The audit query is meta-audited producer-side, so there is NO
 * aggressive auto-refetch — one user query = one call (no background poll
 * loop that would flood meta-audit). A degraded re-query is an explicit
 * user retry (a fresh queryKey or an explicit `refetch()`), never an
 * automatic interval.
 */

const AUDIT_KEY = 'audit';

/** Normalises the filter set into a stable, fully-discriminating query
 *  key (same params ⇒ same key ⇒ one cached call). */
export function auditKey(params: AuditQueryParams) {
  const size = Math.min(
    AUDIT_MAX_PAGE_SIZE,
    Math.max(1, params.size ?? AUDIT_DEFAULT_PAGE_SIZE),
  );
  return [
    AUDIT_KEY,
    params.accountId?.trim() || null,
    params.actionCode?.trim() || null,
    params.from?.trim() || null,
    params.to?.trim() || null,
    params.source ?? null,
    params.tenantId?.trim() || null,
    Math.max(0, params.page ?? 0),
    size,
  ] as const;
}

export function buildAuditQs(params: AuditQueryParams): string {
  const qs = new URLSearchParams();
  if (params.accountId?.trim()) qs.set('accountId', params.accountId.trim());
  if (params.actionCode?.trim())
    qs.set('actionCode', params.actionCode.trim());
  if (params.from?.trim()) qs.set('from', params.from.trim());
  if (params.to?.trim()) qs.set('to', params.to.trim());
  if (params.source) qs.set('source', params.source);
  if (params.tenantId?.trim()) qs.set('tenantId', params.tenantId.trim());
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set(
    'size',
    String(
      Math.min(
        AUDIT_MAX_PAGE_SIZE,
        Math.max(1, params.size ?? AUDIT_DEFAULT_PAGE_SIZE),
      ),
    ),
  );
  return qs.toString();
}

async function fetchAudit(params: AuditQueryParams): Promise<AuditPage> {
  const raw = await apiClient.get<unknown>(
    `/api/audit?${buildAuditQs(params)}`,
  );
  return AuditPageSchema.parse(raw);
}

export function useAuditQuery(
  params: AuditQueryParams,
  initial?: AuditPage,
) {
  return useQuery({
    queryKey: auditKey(params),
    queryFn: () => fetchAudit(params),
    initialData: initial,
    // Seeded from the server render ⇒ that page is fresh (the server
    // already fetched it with the operator token). A filter / page change
    // is a new queryKey → one fresh proxy call. NO refetch interval / NO
    // refetchOnWindowFocus — the producer meta-audits every query.
    staleTime: initial ? 30_000 : 0,
    refetchOnMount: initial ? false : true,
    ...READ_QUERY_REFETCH,
  });
}
