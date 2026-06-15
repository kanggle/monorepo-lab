'use client';

import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/shared/api/client';
import { READ_QUERY_REFETCH } from '@/shared/api/query-options';
import { clampPageSize } from '@/shared/lib/pagination';
import {
  UserListSchema,
  type UserList,
  UserDetailSchema,
  type UserDetail,
  type UserListParams,
  USER_DEFAULT_PAGE_SIZE,
  USER_MAX_PAGE_SIZE,
} from '../api/user-types';

/**
 * Client-side ecommerce-ops user hooks (TASK-PC-FE-084 — architecture.md
 * § Server vs Client Components — React Query is client-only). Every call goes
 * to the same-origin `/api/ecommerce/users/**` proxy (the typed API client's
 * single backend entry point); the proxy attaches the HttpOnly domain-facing
 * IAM OIDC token server-side — the browser never reads a token or calls the
 * ecommerce gateway directly (contract § 2.3 / § 2.4.10).
 *
 * Mirrors `use-ecommerce-orders.ts` exactly: same query-key structure, same
 * seed conventions, same staleTime logic.
 *
 * READ-ONLY: no mutation hooks — the users surface is display-only.
 */

const ECOMMERCE_USERS_KEY = 'ecommerce-users';

const clampSize = (size?: number): number =>
  clampPageSize(size, USER_DEFAULT_PAGE_SIZE, USER_MAX_PAGE_SIZE);

// --- list ------------------------------------------------------------------

export function usersKey(params: UserListParams) {
  return [
    ECOMMERCE_USERS_KEY,
    'list',
    params.status ?? null,
    params.email ?? null,
    Math.max(0, params.page ?? 0),
    clampSize(params.size),
  ] as const;
}

export function buildUsersQs(params: UserListParams): string {
  const qs = new URLSearchParams();
  if (params.status) qs.set('status', params.status);
  if (params.email) qs.set('email', params.email);
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(clampSize(params.size)));
  return qs.toString();
}

async function fetchUsers(params: UserListParams): Promise<UserList> {
  const raw = await apiClient.get<unknown>(
    `/api/ecommerce/users?${buildUsersQs(params)}`,
  );
  return UserListSchema.parse(raw);
}

export function useUsers(params: UserListParams, initial?: UserList) {
  const seeded =
    initial !== undefined &&
    (params.page ?? 0) === 0 &&
    !params.status &&
    !params.email;
  return useQuery({
    queryKey: usersKey(params),
    queryFn: () => fetchUsers(params),
    initialData: seeded ? initial : undefined,
    staleTime: seeded ? 30_000 : 0,
    refetchOnMount: seeded ? false : true,
    ...READ_QUERY_REFETCH,
  });
}

// --- detail ----------------------------------------------------------------

async function fetchUser(id: string): Promise<UserDetail> {
  const raw = await apiClient.get<unknown>(
    `/api/ecommerce/users/${encodeURIComponent(id)}`,
  );
  return UserDetailSchema.parse(raw);
}

export function useUser(id: string | null, initial?: UserDetail) {
  return useQuery({
    queryKey: [ECOMMERCE_USERS_KEY, 'detail', id] as const,
    queryFn: () => fetchUser(id as string),
    enabled: id !== null,
    initialData: initial,
    staleTime: 0,
    ...READ_QUERY_REFETCH,
  });
}
