import { apiClient } from '@/shared/config/api';
import { createAdminUserApi } from '@repo/api-client';
import { isMock, mockGetUsers, mockGetUser } from '@/shared/lib/mock-data';
import type {
  PaginatedResponse,
  AdminUserSummary,
  AdminUserDetail,
  AdminUserListParams,
} from '@repo/types';

const adminUserApi = createAdminUserApi(apiClient);

export async function getUsers(
  params?: AdminUserListParams,
): Promise<PaginatedResponse<AdminUserSummary>> {
  if (isMock()) return mockGetUsers(params);
  return adminUserApi.getUsers(params);
}

export async function getUser(userId: string): Promise<AdminUserDetail> {
  if (isMock()) return mockGetUser(userId);
  return adminUserApi.getUser(userId);
}
