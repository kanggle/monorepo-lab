import { useQuery } from '@tanstack/react-query';
import { createAdminUserApi } from '@repo/api-client';
import { apiClient } from '@/shared/config/api';
import { isMock, mockGetUser } from '@/shared/lib/mock-data';

const adminUserApi = createAdminUserApi(apiClient);

/**
 * userId → email 조회를 위한 얇은 래퍼 훅.
 * user-management feature의 `useUser`와 동일한 Query Key(`['admin', 'users', userId]`)를
 * 공유하여 React Query 캐시 dedupe가 유지되도록 한다.
 *
 * shared 계층은 feature 내부 파일을 참조할 수 없으므로, `@repo/api-client`를 직접 사용한다.
 */
export function useUserEmail(userId: string) {
  const query = useQuery({
    queryKey: ['admin', 'users', userId] as const,
    queryFn: () => (isMock() ? mockGetUser(userId) : adminUserApi.getUser(userId)),
    enabled: !!userId,
  });

  return {
    email: query.data?.email ?? null,
    isLoading: query.isLoading,
    isError: query.isError,
  };
}
