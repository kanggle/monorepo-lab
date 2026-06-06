'use client';

import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import {
  AccountSchema,
  type Account,
  BalancesResponseSchema,
  type BalancesResponse,
  TransactionsResponseSchema,
  type TransactionsResponse,
  type TransactionsQueryParams,
  FINANCE_DEFAULT_PAGE_SIZE,
  FINANCE_MAX_PAGE_SIZE,
} from '../api/types';

/**
 * Client-side finance-ops hooks (architecture.md § Server vs Client
 * Components — React Query is client-only). Every call goes to the
 * same-origin `/api/finance/**` proxy (the typed API client's single
 * backend entry point); the proxy attaches the HttpOnly **IAM OIDC
 * access token** server-side — the browser never reads a token or
 * calls finance directly (contract § 2.3). The § 2.4.5 per-domain
 * credential rule is reused, NOT re-derived (§ 2.4.7).
 *
 * READ-ONLY: there are NO mutation hooks at all (finance v1 has no
 * operator-mutation parity; the section is account-id-driven reads).
 * The hooks below are pure reads.
 *
 * No tight refetch loop / refetchInterval / refetchOnWindowFocus — a
 * re-query is an `accountId` / filter / page change (a new queryKey) or
 * an explicit user retry. **No 429 / Retry-After / backoff branch**
 * (§ 2.4.7 — finance has no documented 429; React Query `retry: false`
 * means a finance failure surfaces immediately, no client retry).
 */

const FINANCE_KEY = 'finance-ops';

function clampSize(size?: number): number {
  // Page size arithmetic — NOT money. F5 invariant is amount-only.
  return Math.min(
    FINANCE_MAX_PAGE_SIZE,
    Math.max(1, size ?? FINANCE_DEFAULT_PAGE_SIZE),
  );
}

// --- account by id read --------------------------------------------------

export function accountKey(accountId: string | null) {
  return [FINANCE_KEY, 'account', accountId ?? ''] as const;
}

async function fetchAccount(accountId: string): Promise<Account> {
  const raw = await apiClient.get<unknown>(
    `/api/finance/accounts/${encodeURIComponent(accountId)}`,
  );
  return AccountSchema.parse(raw);
}

export function useFinanceAccount(accountId: string | null) {
  return useQuery({
    queryKey: accountKey(accountId),
    queryFn: () => fetchAccount(accountId as string),
    enabled: Boolean(accountId && accountId.trim()),
    staleTime: 15_000,
    refetchOnMount: false,
    refetchOnWindowFocus: false,
    refetchInterval: false,
    retry: false,
  });
}

// --- balances read -------------------------------------------------------

export function balancesKey(accountId: string | null) {
  return [FINANCE_KEY, 'balances', accountId ?? ''] as const;
}

async function fetchBalances(accountId: string): Promise<BalancesResponse> {
  const raw = await apiClient.get<unknown>(
    `/api/finance/accounts/${encodeURIComponent(accountId)}/balances`,
  );
  return BalancesResponseSchema.parse(raw);
}

export function useFinanceBalances(
  accountId: string | null,
  initial?: BalancesResponse,
) {
  return useQuery({
    queryKey: balancesKey(accountId),
    queryFn: () => fetchBalances(accountId as string),
    enabled: Boolean(accountId && accountId.trim()),
    initialData: initial,
    staleTime: 15_000,
    refetchOnMount: false,
    refetchOnWindowFocus: false,
    refetchInterval: false,
    retry: false,
  });
}

// --- transactions read (paginated, type+status filters) ------------------

export function transactionsKey(
  accountId: string | null,
  params: TransactionsQueryParams,
) {
  return [
    FINANCE_KEY,
    'transactions',
    accountId ?? '',
    params.type ?? null,
    params.status ?? null,
    Math.max(0, params.page ?? 0),
    clampSize(params.size),
  ] as const;
}

export function buildTransactionsQs(params: TransactionsQueryParams): string {
  const qs = new URLSearchParams();
  if (params.type) qs.set('type', params.type);
  if (params.status) qs.set('status', params.status);
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(clampSize(params.size)));
  return qs.toString();
}

async function fetchTransactions(
  accountId: string,
  params: TransactionsQueryParams,
): Promise<TransactionsResponse> {
  const raw = await apiClient.get<unknown>(
    `/api/finance/accounts/${encodeURIComponent(accountId)}/transactions?${buildTransactionsQs(params)}`,
  );
  return TransactionsResponseSchema.parse(raw);
}

export function useFinanceTransactions(
  accountId: string | null,
  params: TransactionsQueryParams,
  initial?: TransactionsResponse,
) {
  const seeded =
    initial !== undefined &&
    (params.page ?? 0) === 0 &&
    !params.type &&
    !params.status;
  return useQuery({
    queryKey: transactionsKey(accountId, params),
    queryFn: () => fetchTransactions(accountId as string, params),
    enabled: Boolean(accountId && accountId.trim()),
    initialData: seeded ? initial : undefined,
    staleTime: seeded ? 30_000 : 0,
    refetchOnMount: seeded ? false : true,
    refetchOnWindowFocus: false,
    refetchInterval: false,
    retry: false,
  });
}
