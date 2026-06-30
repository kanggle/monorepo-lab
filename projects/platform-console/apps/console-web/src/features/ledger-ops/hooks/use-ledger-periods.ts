'use client';

import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import { READ_QUERY_REFETCH } from '@/shared/api/query-options';
import {
  TrialBalanceSchema,
  type TrialBalance,
  PeriodSchema,
  type Period,
  PeriodsResponseSchema,
  type PeriodsResponse,
  type PeriodsQueryParams,
} from '../api/types';
import { LEDGER_KEY, clampSize } from './use-ledger-shared';

/**
 * Ledger-ops trial-balance + accounting-periods read hooks (TASK-PC-FE-148
 * split). Pure reads — every call goes to the same-origin `/api/ledger/**`
 * proxy (the typed API client's single backend entry point); the proxy
 * attaches the HttpOnly IAM OIDC access token server-side (contract § 2.3 /
 * § 2.4.7.1 reuse). `retry: false` / no refetch-storm posture, same as the
 * other ledger reads. Behavior-preserving extraction — names / signatures /
 * queryKeys / call order are verbatim from the pre-split `use-ledger-ops`.
 */

// --- trial balance read --------------------------------------------------

export function trialBalanceKey() {
  return [LEDGER_KEY, 'trial-balance'] as const;
}

async function fetchTrialBalance(): Promise<TrialBalance> {
  const raw = await apiClient.get<unknown>('/api/ledger/trial-balance');
  return TrialBalanceSchema.parse(raw);
}

export function useTrialBalance(initial?: TrialBalance) {
  return useQuery({
    queryKey: trialBalanceKey(),
    queryFn: fetchTrialBalance,
    initialData: initial,
    staleTime: 30_000,
    refetchOnMount: false,
    ...READ_QUERY_REFETCH,
    retry: false,
  });
}

// --- periods list read ---------------------------------------------------

export function periodsKey(params: PeriodsQueryParams) {
  return [
    LEDGER_KEY,
    'periods',
    Math.max(0, params.page ?? 0),
    clampSize(params.size),
  ] as const;
}

export function buildPeriodsQs(params: PeriodsQueryParams): string {
  const qs = new URLSearchParams();
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(clampSize(params.size)));
  return qs.toString();
}

async function fetchPeriods(
  params: PeriodsQueryParams,
): Promise<PeriodsResponse> {
  const raw = await apiClient.get<unknown>(
    `/api/ledger/periods?${buildPeriodsQs(params)}`,
  );
  return PeriodsResponseSchema.parse(raw);
}

export function usePeriods(
  params: PeriodsQueryParams,
  initial?: PeriodsResponse,
) {
  const seeded = initial !== undefined && (params.page ?? 0) === 0;
  return useQuery({
    queryKey: periodsKey(params),
    queryFn: () => fetchPeriods(params),
    initialData: seeded ? initial : undefined,
    staleTime: seeded ? 30_000 : 0,
    refetchOnMount: seeded ? false : true,
    ...READ_QUERY_REFETCH,
    retry: false,
  });
}

// --- period detail read --------------------------------------------------

export function periodKey(periodId: string | null) {
  return [LEDGER_KEY, 'period', periodId ?? ''] as const;
}

async function fetchPeriod(periodId: string): Promise<Period> {
  const raw = await apiClient.get<unknown>(
    `/api/ledger/periods/${encodeURIComponent(periodId)}`,
  );
  return PeriodSchema.parse(raw);
}

export function usePeriod(periodId: string | null) {
  return useQuery({
    queryKey: periodKey(periodId),
    queryFn: () => fetchPeriod(periodId as string),
    enabled: Boolean(periodId && periodId.trim()),
    staleTime: 30_000,
    refetchOnMount: false,
    ...READ_QUERY_REFETCH,
    retry: false,
  });
}
