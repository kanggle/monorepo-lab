import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createElement } from 'react';
import { useOrders } from '@/features/order-management/hooks/use-orders';

const mockPush = vi.fn();
let mockSearchParams = new URLSearchParams();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush }),
  useSearchParams: () => mockSearchParams,
}));

const mockGetOrders = vi.fn().mockResolvedValue({
  content: [
    { orderId: 'o1', userId: 'u1', status: 'PENDING', totalPrice: 30000, itemCount: 2, createdAt: '2026-03-20T10:00:00Z' },
  ],
  totalElements: 1,
  page: 0,
  size: 20,
});

vi.mock('@/features/order-management/api/order-api', () => ({
  getOrders: (...args: unknown[]) => mockGetOrders(...args),
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children);
}

describe('useOrders', () => {
  beforeEach(() => {
    mockPush.mockClear();
    mockGetOrders.mockClear();
    mockSearchParams = new URLSearchParams();
  });

  it('주문 목록을 조회한다', async () => {
    const { result } = renderHook(() => useOrders(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(result.current.data?.content).toHaveLength(1);
    expect(result.current.data?.content[0].orderId).toBe('o1');
  });

  it('pagination 정보를 반환한다', async () => {
    const { result } = renderHook(() => useOrders(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(result.current.pagination.page).toBe(0);
    expect(result.current.pagination.totalPages).toBe(1);
  });

  it('setFilter로 상태 필터를 변경한다', async () => {
    const { result } = renderHook(() => useOrders(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    result.current.filters.setFilter('status', 'PENDING');
    expect(mockPush).toHaveBeenCalledWith('?status=PENDING&page=0');
  });

  it('setFilter로 필터를 해제하면 파라미터가 삭제된다', async () => {
    mockSearchParams = new URLSearchParams('status=PENDING');

    const { result } = renderHook(() => useOrders(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    result.current.filters.setFilter('status', undefined);
    expect(mockPush).toHaveBeenCalledWith('?page=0');
  });

  it('setPage로 페이지를 변경한다', async () => {
    const { result } = renderHook(() => useOrders(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    result.current.pagination.onPageChange(2);
    expect(mockPush).toHaveBeenCalledWith('?page=2');
  });

  it('status 필터가 설정되면 API에 status를 전달한다', async () => {
    mockSearchParams = new URLSearchParams('status=PENDING');

    const { result } = renderHook(() => useOrders(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(mockGetOrders).toHaveBeenCalledWith({ page: 0, status: 'PENDING' });
  });

  it('status 필터가 없으면 API에 status를 전달하지 않는다', async () => {
    const { result } = renderHook(() => useOrders(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(mockGetOrders).toHaveBeenCalledWith({ page: 0 });
  });

  it('status 필터와 page를 동시에 적용한다', async () => {
    mockSearchParams = new URLSearchParams('status=CONFIRMED&page=2');

    const { result } = renderHook(() => useOrders(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(mockGetOrders).toHaveBeenCalledWith({ page: 2, status: 'CONFIRMED' });
  });

  it('유효하지 않은 status 값은 무시한다', async () => {
    mockSearchParams = new URLSearchParams('status=INVALID');

    const { result } = renderHook(() => useOrders(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(result.current.filters.status).toBeUndefined();
    expect(mockGetOrders).toHaveBeenCalledWith({ page: 0 });
  });
});
