'use client';

import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import { READ_QUERY_REFETCH } from '@/shared/api/query-options';
import {
  AccountLinkProposalListResponseSchema,
  type AccountLinkProposalListResponse,
  type AccountLinkProposalAction,
} from '../../api/types';
import {
  employeeAccountLinkProposalsKey,
  myAccountLinkProposalsKey,
} from '../../api/erp-keys';
import { invalidateMaster } from '../use-erp-shared';

/**
 * erp employee ↔ IAM account link hooks (TASK-PC-FE-318 — `TASK-MONO-774` S4).
 * Every call goes to the same-origin `/api/erp/masterdata/**` proxy (the token
 * is attached server-side). A successful write invalidates the whole
 * `[ERP_KEY, 'employees']` prefix: the list/detail «연결된 계정» cell, the
 * employee's proposal history and «my proposals» all live under it.
 *
 * No polling (Out of Scope: live refresh — a reload is enough).
 */

const LINK_PAGE_SIZE = 20;

async function fetchProposals(path: string): Promise<AccountLinkProposalListResponse> {
  const raw = await apiClient.get<unknown>(path);
  return AccountLinkProposalListResponseSchema.parse(raw);
}

/** PENDING proposals addressed to the signed-in account (accept / decline). */
export function useMyAccountLinkProposals() {
  return useQuery({
    queryKey: myAccountLinkProposalsKey(),
    queryFn: () =>
      fetchProposals(
        `/api/erp/masterdata/account-link-proposals/mine?page=0&size=${LINK_PAGE_SIZE}`,
      ),
    staleTime: 0,
    ...READ_QUERY_REFETCH,
    retry: false,
  });
}

/** One employee's proposal history (every status, newest first). */
export function useEmployeeAccountLinkProposals(employeeId: string | null) {
  return useQuery({
    queryKey: employeeAccountLinkProposalsKey(employeeId ?? ''),
    queryFn: () =>
      fetchProposals(
        `/api/erp/masterdata/employees/${encodeURIComponent(
          employeeId as string,
        )}/account-link-proposals?page=0&size=${LINK_PAGE_SIZE}`,
      ),
    enabled: Boolean(employeeId && employeeId.trim()),
    staleTime: 0,
    ...READ_QUERY_REFETCH,
    retry: false,
  });
}

export function useProposeAccountLink() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (args: {
      employeeId: string;
      accountId: string;
      reason?: string;
      idempotencyKey: string;
    }) =>
      apiClient.post<unknown>(
        `/api/erp/masterdata/employees/${encodeURIComponent(
          args.employeeId,
        )}/account-link-proposals`,
        {
          accountId: args.accountId,
          ...(args.reason ? { reason: args.reason } : {}),
          idempotencyKey: args.idempotencyKey,
        },
      ),
    onSuccess: () => invalidateMaster(qc, 'employees'),
  });
}

/** accept / decline / revoke — one hook per action over the shared route. */
export function useAccountLinkProposalAction(action: AccountLinkProposalAction) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (args: {
      proposalId: string;
      reason?: string;
      idempotencyKey: string;
    }) =>
      apiClient.post<unknown>(
        `/api/erp/masterdata/account-link-proposals/${encodeURIComponent(
          args.proposalId,
        )}/${action}`,
        {
          ...(args.reason ? { reason: args.reason } : {}),
          idempotencyKey: args.idempotencyKey,
        },
      ),
    onSuccess: () => invalidateMaster(qc, 'employees'),
  });
}

export function useUnlinkEmployeeAccount() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (args: {
      employeeId: string;
      reason: string;
      idempotencyKey: string;
    }) =>
      apiClient.post<unknown>(
        `/api/erp/masterdata/employees/${encodeURIComponent(
          args.employeeId,
        )}/account-link/unlink`,
        { reason: args.reason, idempotencyKey: args.idempotencyKey },
      ),
    onSuccess: () => invalidateMaster(qc, 'employees'),
  });
}
