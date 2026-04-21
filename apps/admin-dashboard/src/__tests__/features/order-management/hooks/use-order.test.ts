import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createElement } from 'react';
import { useOrder } from '@/features/order-management/hooks/use-order';

const mockOrder = {
  orderId: 'o1',
  status: 'PENDING' as const,
  totalPrice: 30000,
  items: [
    { productId: 'p1', variantId: 'v1', productName: '상품 A', optionName: '기본', quantity: 2, unitPrice: 15000 },
  ],
  shippingAddress: {
    recipient: '홍길동',
    phone: '010-1234-5678',
    zipCode: '12345',
    address1: '서울시 강남구',
    address2: '101호',
  },
  createdAt: '2026-03-20T10:00:00Z',
  updatedAt: '2026-03-20T10:00:00Z',
};

const mockGetOrder = vi.fn().mockResolvedValue(mockOrder);

vi.mock('@/features/order-management/api/order-api', () => ({
  getOrder: (...args: unknown[]) => mockGetOrder(...args),
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children);
}

describe('useOrder', () => {
  beforeEach(() => {
    mockGetOrder.mockClear();
  });

  it('주문 상세를 조회한다', async () => {
    const { result } = renderHook(() => useOrder('o1'), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(result.current.data?.orderId).toBe('o1');
    expect(result.current.data?.items).toHaveLength(1);
  });

  it('orderId가 빈 문자열이면 쿼리를 실행하지 않는다', () => {
    const { result } = renderHook(() => useOrder(''), {
      wrapper: createWrapper(),
    });

    expect(result.current.fetchStatus).toBe('idle');
    expect(mockGetOrder).not.toHaveBeenCalled();
  });
});
