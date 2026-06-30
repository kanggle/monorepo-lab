'use client';

import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import { READ_QUERY_REFETCH } from '@/shared/api/query-options';
import {
  JournalEntrySchema,
  type JournalEntry,
  AccountBalanceSchema,
  type AccountBalance,
  AccountEntriesResponseSchema,
  type AccountEntriesResponse,
  type AccountEntriesQueryParams,
} from '../api/types';
import { LEDGER_KEY, clampSize } from './use-ledger-shared';

/**
 * Ledger-ops journal-entry + account (balance / entries) read hooks
 * (TASK-PC-FE-148 split — id-driven reads, no list/search for entries).
 * Pure reads — every call goes to the same-origin `/api/ledger/**` proxy;
 * the proxy attaches the HttpOnly IAM OIDC access token server-side — NEVER
 * the operator token (§ 2.4.7.1 reuse). `retry: false` / no refetch-storm
 * posture, same as the other ledger reads. Behavior-preserving extraction —
 * names / signatures / queryKeys / call order verbatim from the pre-split file.
 */

// --- journal entry read (id-driven; no list/search) ----------------------

export function journalEntryKey(entryId: string | null) {
  return [LEDGER_KEY, 'entry', entryId ?? ''] as const;
}

async function fetchJournalEntry(entryId: string): Promise<JournalEntry> {
  const raw = await apiClient.get<unknown>(
    `/api/ledger/entries/${encodeURIComponent(entryId)}`,
  );
  return JournalEntrySchema.parse(raw);
}

export function useJournalEntry(
  entryId: string | null,
  initial?: JournalEntry,
) {
  const seeded = initial !== undefined && Boolean(entryId);
  return useQuery({
    queryKey: journalEntryKey(entryId),
    queryFn: () => fetchJournalEntry(entryId as string),
    enabled: Boolean(entryId && entryId.trim()),
    initialData: seeded ? initial : undefined,
    staleTime: 30_000,
    refetchOnMount: false,
    ...READ_QUERY_REFETCH,
    retry: false,
  });
}

// --- account balance read (id-driven; TASK-PC-FE-074) ---------------------

export function accountBalanceKey(code: string | null) {
  return [LEDGER_KEY, 'account-balance', code ?? ''] as const;
}

async function fetchAccountBalance(code: string): Promise<AccountBalance> {
  const raw = await apiClient.get<unknown>(
    `/api/ledger/accounts/${encodeURIComponent(code)}/balance`,
  );
  return AccountBalanceSchema.parse(raw);
}

/**
 * `useAccountBalance` — reads the running balance for a ledger account.
 * READ-ONLY. The same-origin proxy attaches the domain-facing IAM OIDC
 * access token server-side — NEVER the operator token (§ 2.4.7.1 reuse).
 * `retry: false` / no-refetch-storm posture, same as the other ledger reads.
 * `initialData` is used when the server-seeded `initial` matches the
 * requested `code` (to avoid a double-fetch on SSR→CSR transition).
 */
export function useAccountBalance(
  code: string | null,
  initial?: AccountBalance,
) {
  const seeded = initial !== undefined && Boolean(code);
  return useQuery({
    queryKey: accountBalanceKey(code),
    queryFn: () => fetchAccountBalance(code as string),
    enabled: Boolean(code && code.trim()),
    initialData: seeded ? initial : undefined,
    staleTime: 30_000,
    refetchOnMount: false,
    ...READ_QUERY_REFETCH,
    retry: false,
  });
}

// --- account entries read (paginated; TASK-PC-FE-074) ---------------------

export function accountEntriesKey(
  code: string | null,
  params: AccountEntriesQueryParams,
) {
  return [
    LEDGER_KEY,
    'account-entries',
    code ?? '',
    Math.max(0, params.page ?? 0),
    clampSize(params.size),
  ] as const;
}

export function buildAccountEntriesQs(params: AccountEntriesQueryParams): string {
  const qs = new URLSearchParams();
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(clampSize(params.size)));
  return qs.toString();
}

async function fetchAccountEntries(
  code: string,
  params: AccountEntriesQueryParams,
): Promise<AccountEntriesResponse> {
  const raw = await apiClient.get<unknown>(
    `/api/ledger/accounts/${encodeURIComponent(code)}/entries?${buildAccountEntriesQs(params)}`,
  );
  return AccountEntriesResponseSchema.parse(raw);
}

/**
 * `useAccountEntries` — reads the paginated journal lines posted to one
 * account. READ-ONLY. Same posture as `usePeriods` / `useDiscrepancies`.
 * `initialData` is used when the server-seeded `initial` matches page 0.
 */
export function useAccountEntries(
  code: string | null,
  params: AccountEntriesQueryParams,
  initial?: AccountEntriesResponse,
) {
  const seeded = initial !== undefined && (params.page ?? 0) === 0;
  return useQuery({
    queryKey: accountEntriesKey(code, params),
    queryFn: () => fetchAccountEntries(code as string, params),
    enabled: Boolean(code && code.trim()),
    initialData: seeded ? initial : undefined,
    staleTime: seeded ? 30_000 : 0,
    refetchOnMount: seeded ? false : true,
    ...READ_QUERY_REFETCH,
    retry: false,
  });
}
