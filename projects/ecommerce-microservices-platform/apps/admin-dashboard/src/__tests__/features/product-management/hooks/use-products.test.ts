import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createElement } from 'react';
import { useProducts } from '@/features/product-management/hooks/use-products';

const mockPush = vi.fn();
let mockSearchParams = new URLSearchParams();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush }),
  useSearchParams: () => mockSearchParams,
}));

vi.mock('@/features/product-management/api/product-api', () => ({
  getProducts: vi.fn().mockResolvedValue({
    content: [
      { id: '1', name: '상품 A', price: 10000, status: 'ON_SALE', thumbnailUrl: '', categoryId: 'cat1' },
    ],
    totalPages: 1,
    totalElements: 1,
    page: 0,
    size: 20,
  }),
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children);
}

describe('useProducts', () => {
  beforeEach(() => {
    mockPush.mockClear();
    mockSearchParams = new URLSearchParams();
  });

  it('상품 목록을 조회한다', async () => {
    const { result } = renderHook(() => useProducts(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(result.current.data?.content).toHaveLength(1);
    expect(result.current.data?.content[0].name).toBe('상품 A');
  });

  it('pagination 정보를 반환한다', async () => {
    const { result } = renderHook(() => useProducts(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(result.current.pagination.page).toBe(0);
    expect(result.current.pagination.totalPages).toBe(1);
  });

  it('setFilter로 URL 파라미터를 변경한다', async () => {
    const { result } = renderHook(() => useProducts(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    result.current.filters.setFilter('status', 'ON_SALE');
    expect(mockPush).toHaveBeenCalledWith('?status=ON_SALE&page=0');
  });

  it('name 필터를 API에 전달한다', async () => {
    const { getProducts } = await import(
      '@/features/product-management/api/product-api'
    );

    mockSearchParams = new URLSearchParams('name=테스트');

    const { result } = renderHook(() => useProducts(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(getProducts).toHaveBeenCalledWith(
      expect.objectContaining({ name: '테스트' }),
    );
  });

  it('name 필터가 빈 문자열이면 undefined로 전달한다', async () => {
    const { result } = renderHook(() => useProducts(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(result.current.filters.name).toBeUndefined();
  });

  it('setFilter로 name 필터를 설정하면 page가 0으로 리셋된다', async () => {
    const { result } = renderHook(() => useProducts(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    result.current.filters.setFilter('name', '검색어');
    expect(mockPush).toHaveBeenCalledWith(expect.stringContaining('page=0'));
  });
});
