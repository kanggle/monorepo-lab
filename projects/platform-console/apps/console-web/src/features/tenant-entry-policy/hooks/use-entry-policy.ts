'use client';

import { useMutation, useQuery } from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import {
  EntryPolicySchema,
  EnrolmentSummarySchema,
  type EntryPolicy,
  type EnrolmentSummary,
} from '../api/types';

/**
 * Client hooks for the entry-policy control (TASK-MONO-771 S5). Every call goes
 * to the same-origin `/api/tenants/{tenantId}/entry-policy/**` BFF, which
 * attaches the HttpOnly operator token + active tenant server-side.
 */

const base = (tenantId: string) =>
  `/api/tenants/${encodeURIComponent(tenantId)}/entry-policy`;

/**
 * The «켜기 전 사전 점검» read — only fetched while the «켜기» confirm is open.
 * Advisory: a failure leaves `data` undefined and the dialog shows its copy
 * without a number (never a made-up `0`). No retry — a 503 stays a 503.
 */
export function useEnrolmentSummary(tenantId: string, enabled: boolean) {
  return useQuery<EnrolmentSummary>({
    queryKey: ['tenant-entry-policy', 'enrolment-summary', tenantId] as const,
    queryFn: async () =>
      EnrolmentSummarySchema.parse(
        await apiClient.get<unknown>(`${base(tenantId)}/enrolment-summary`),
      ),
    enabled,
    retry: false,
    staleTime: 0,
    refetchOnWindowFocus: false,
  });
}

interface SetArgs {
  requireMfa: boolean;
  reason: string;
}

/** PUT — the reason is operator-typed and required; the hook never fabricates one. */
export function useSetEntryPolicy(tenantId: string) {
  return useMutation<EntryPolicy, Error, SetArgs>({
    mutationFn: async ({ requireMfa, reason }) =>
      EntryPolicySchema.parse(
        await apiClient.put<unknown>(base(tenantId), { requireMfa, reason }),
      ),
  });
}
