import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createElement } from 'react';
import { useUsers } from '@/features/user-management/hooks/use-users';

const mockPush = vi.fn();
let mockSearchParams = new URLSearchParams();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush }),
  useSearchParams: () => mockSearchParams,
}));

const mockGetUsers = vi.fn().mockResolvedValue({
  content: [
    { userId: 'u1', email: 'user1@example.com', name: '홍길동', nickname: '길동', status: 'ACTIVE', createdAt: '2026-03-20T10:00:00Z' },
  ],
  totalElements: 1,
  page: 0,
  size: 20,
});

vi.mock('@/features/user-management/api/user-api', () => ({
  getUsers: (...args: unknown[]) => mockGetUsers(...args),
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children);
}

describe('useUsers', () => {
  beforeEach(() => {
    mockPush.mockClear();
    mockGetUsers.mockClear();
    mockSearchParams = new URLSearchParams();
  });

  it('사용자 목록을 조회한다', async () => {
    const { result } = renderHook(() => useUsers(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(result.current.data?.content).toHaveLength(1);
    expect(result.current.data?.content[0].userId).toBe('u1');
  });

  it('pagination 정보를 반환한다', async () => {
    const { result } = renderHook(() => useUsers(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(result.current.pagination.page).toBe(0);
    expect(result.current.pagination.totalPages).toBe(1);
  });

  it('setFilter로 상태 필터를 변경한다', async () => {
    const { result } = renderHook(() => useUsers(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    result.current.filters.setFilter('status', 'ACTIVE');
    expect(mockPush).toHaveBeenCalledWith('?status=ACTIVE&page=0');
  });

  it('setFilter로 이메일 필터를 변경한다', async () => {
    const { result } = renderHook(() => useUsers(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    result.current.filters.setFilter('email', 'test@example.com');
    expect(mockPush).toHaveBeenCalledWith('?email=test%40example.com&page=0');
  });

  it('setFilter로 필터를 해제하면 파라미터가 삭제된다', async () => {
    mockSearchParams = new URLSearchParams('status=ACTIVE');

    const { result } = renderHook(() => useUsers(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    result.current.filters.setFilter('status', undefined);
    expect(mockPush).toHaveBeenCalledWith('?page=0');
  });

  it('setPage로 페이지를 변경한다', async () => {
    const { result } = renderHook(() => useUsers(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    result.current.pagination.onPageChange(2);
    expect(mockPush).toHaveBeenCalledWith('?page=2');
  });

  it('status와 email 필터가 설정되면 API에 전달한다', async () => {
    mockSearchParams = new URLSearchParams('status=SUSPENDED&email=test@example.com');

    const { result } = renderHook(() => useUsers(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(mockGetUsers).toHaveBeenCalledWith({
      page: 0,
      status: 'SUSPENDED',
      email: 'test@example.com',
    });
  });

  it('유효하지 않은 status 값은 무시한다', async () => {
    mockSearchParams = new URLSearchParams('status=INVALID');

    const { result } = renderHook(() => useUsers(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(result.current.filters.status).toBeUndefined();
    expect(mockGetUsers).toHaveBeenCalledWith({ page: 0 });
  });
});
