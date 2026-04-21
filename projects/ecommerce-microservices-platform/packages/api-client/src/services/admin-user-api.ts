import type { ApiClient } from '../client';
import type {
  PaginatedResponse,
  AdminUserSummary,
  AdminUserDetail,
  AdminUserListParams,
} from '@repo/types';

export function createAdminUserApi(client: ApiClient) {
  return {
    getUsers: (params?: AdminUserListParams) =>
      client.get<PaginatedResponse<AdminUserSummary>>('/api/admin/users', {
        params,
      }),

    getUser: (userId: string) =>
      client.get<AdminUserDetail>(`/api/admin/users/${userId}`),
  };
}
