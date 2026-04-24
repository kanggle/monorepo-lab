import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createElement } from 'react';
import { useUser } from '@/features/user-management/hooks/use-user';

const mockUser = {
  userId: 'u1',
  email: 'user1@example.com',
  name: '홍길동',
  nickname: '길동',
  phone: '010-1234-5678',
  profileImageUrl: null,
  status: 'ACTIVE' as const,
  createdAt: '2026-03-20T10:00:00Z',
  updatedAt: '2026-03-20T10:00:00Z',
};

const mockGetUser = vi.fn().mockResolvedValue(mockUser);

vi.mock('@/features/user-management/api/user-api', () => ({
  getUser: (...args: unknown[]) => mockGetUser(...args),
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children);
}

describe('useUser', () => {
  beforeEach(() => {
    mockGetUser.mockClear();
  });

  it('사용자 상세를 조회한다', async () => {
    const { result } = renderHook(() => useUser('u1'), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(result.current.data?.userId).toBe('u1');
    expect(result.current.data?.email).toBe('user1@example.com');
    expect(result.current.data?.name).toBe('홍길동');
  });

  it('userId가 빈 문자열이면 쿼리를 실행하지 않는다', () => {
    const { result } = renderHook(() => useUser(''), {
      wrapper: createWrapper(),
    });

    expect(result.current.fetchStatus).toBe('idle');
    expect(mockGetUser).not.toHaveBeenCalled();
  });

  it('API 에러 시 에러 상태를 반환한다', async () => {
    mockGetUser.mockRejectedValueOnce({ code: 'USER_PROFILE_NOT_FOUND', message: 'User not found', timestamp: '2026-03-20T10:00:00Z' });

    const { result } = renderHook(() => useUser('not-exist'), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
    });

    expect(result.current.error).toEqual(
      expect.objectContaining({ code: 'USER_PROFILE_NOT_FOUND' }),
    );
  });
});
