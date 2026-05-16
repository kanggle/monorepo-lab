'use client';

import {
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import {
  AccountPageSchema,
  type AccountPage,
  type AccountSearchParams,
  LockResultSchema,
  type LockResult,
  UnlockResultSchema,
  type UnlockResult,
  BulkLockResultSchema,
  type BulkLockResult,
  RevokeSessionResultSchema,
  type RevokeSessionResult,
  GdprDeleteResultSchema,
  type GdprDeleteResult,
  type MutationReason,
} from '../api/types';

/**
 * Client-side accounts hooks (architecture.md § Server vs Client Components —
 * React Query is client-only). Every call goes to the same-origin
 * `/api/accounts/**` proxy (the typed API client's single backend entry
 * point); the proxy attaches the HttpOnly operator token + tenant
 * server-side — the browser never reads a token or calls GAP directly
 * (contract § 2.3).
 *
 * Idempotency-Key (§ 2.4.1 / integration-heavy I4): generated once per a
 * single user-confirmed action via `crypto.randomUUID()`, supplied by the
 * confirm-dialog when it fires the mutation, and reused only if that exact
 * confirmed action is retried; a fresh user action generates a new key.
 * The hooks themselves never fabricate a reason — it is required input.
 */

const ACCOUNTS_KEY = 'accounts';

function searchKey(params: AccountSearchParams) {
  return [
    ACCOUNTS_KEY,
    params.email ?? null,
    params.page ?? 0,
    params.size ?? 20,
  ] as const;
}

// --- read: search / list --------------------------------------------------

async function querySearch(
  params: AccountSearchParams,
): Promise<AccountPage> {
  const qs = new URLSearchParams();
  if (params.email && params.email.trim() !== '') {
    qs.set('email', params.email.trim());
  } else {
    qs.set('page', String(params.page ?? 0));
    qs.set('size', String(params.size ?? 20));
  }
  const raw = await apiClient.get<unknown>(`/api/accounts?${qs.toString()}`);
  return AccountPageSchema.parse(raw);
}

export function useAccountsSearch(
  params: AccountSearchParams,
  initial?: AccountPage,
) {
  return useQuery({
    queryKey: searchKey(params),
    queryFn: () => querySearch(params),
    initialData: initial,
    // When seeded from the server render, treat that page as fresh so we
    // don't immediately re-fetch the same page (the server already fetched
    // it with the operator token). A pagination / search change is a new
    // queryKey → a fresh proxy call. Re-query on a degraded section is an
    // explicit user retry, not an automatic background poll.
    staleTime: initial ? 30_000 : 0,
    refetchOnMount: initial ? false : true,
  });
}

// --- mutations (each requires an operator-entered reason) ------------------

interface MutationArgs {
  accountId: string;
  reason: MutationReason;
  /** Stable per the confirmed action (the confirm-dialog generates it). */
  idempotencyKey: string;
}

function invalidateAccounts(qc: ReturnType<typeof useQueryClient>) {
  qc.invalidateQueries({ queryKey: [ACCOUNTS_KEY] });
}

export function useLockAccount() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ accountId, reason, idempotencyKey }: MutationArgs) => {
      const raw = await apiClient.post<unknown>(
        `/api/accounts/${encodeURIComponent(accountId)}/lock`,
        { ...reason, idempotencyKey },
      );
      return LockResultSchema.parse(raw) as LockResult;
    },
    onSuccess: () => invalidateAccounts(qc),
  });
}

export function useUnlockAccount() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ accountId, reason, idempotencyKey }: MutationArgs) => {
      const raw = await apiClient.post<unknown>(
        `/api/accounts/${encodeURIComponent(accountId)}/unlock`,
        { ...reason, idempotencyKey },
      );
      return UnlockResultSchema.parse(raw) as UnlockResult;
    },
    onSuccess: () => invalidateAccounts(qc),
  });
}

export function useRevokeSessions() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ accountId, reason, idempotencyKey }: MutationArgs) => {
      const raw = await apiClient.post<unknown>(
        `/api/accounts/${encodeURIComponent(accountId)}/revoke-session`,
        { reason: reason.reason, idempotencyKey },
      );
      return RevokeSessionResultSchema.parse(raw) as RevokeSessionResult;
    },
    onSuccess: () => invalidateAccounts(qc),
  });
}

export function useGdprDeleteAccount() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ accountId, reason, idempotencyKey }: MutationArgs) => {
      const raw = await apiClient.post<unknown>(
        `/api/accounts/${encodeURIComponent(accountId)}/gdpr-delete`,
        { ...reason, idempotencyKey },
      );
      return GdprDeleteResultSchema.parse(raw) as GdprDeleteResult;
    },
    onSuccess: () => invalidateAccounts(qc),
  });
}

interface BulkLockArgs {
  accountIds: string[];
  reason: MutationReason;
  idempotencyKey: string;
}

export function useBulkLockAccounts() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({
      accountIds,
      reason,
      idempotencyKey,
    }: BulkLockArgs) => {
      const raw = await apiClient.post<unknown>('/api/accounts/bulk-lock', {
        accountIds,
        ...reason,
        idempotencyKey,
      });
      return BulkLockResultSchema.parse(raw) as BulkLockResult;
    },
    // Per-account partial failure is normal — invalidate so the table
    // reflects whatever did change.
    onSettled: () => invalidateAccounts(qc),
  });
}
