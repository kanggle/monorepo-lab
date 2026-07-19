import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type {
  OrderDetail,
  ApiErrorResponse,
  PaymentResponse,
  CancelOrderResponse,
} from '@repo/types';
import { TestQueryProvider } from './test-utils';

const mockReplace = vi.fn();
vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: mockReplace, push: vi.fn() }),
  useParams: vi.fn(),
}));

vi.mock('@/features/auth', () => ({
  useAuth: vi.fn(),
  useRequireAuth: vi.fn(),
}));

vi.mock('@/entities/order', () => ({
  getOrder: vi.fn(),
  cancelOrder: vi.fn(),
  OrderStatusBadge: ({ status }: { status: string }) => <span data-testid="status-badge">{status}</span>,
}));

vi.mock('@/entities/payment', () => ({
  getPayment: vi.fn(),
  PaymentStatusBadge: ({ status }: { status: string }) => <span data-testid="payment-status-badge">{status}</span>,
}));

vi.mock('@repo/ui', () => ({
  LoadingSpinner: () => <div data-testid="loading-spinner">로딩 중...</div>,
  ErrorMessage: ({ message, onRetry }: { message: string; onRetry: () => void }) => (
    <div data-testid="error-message">
      {message}
      <button onClick={onRetry}>재시도</button>
    </div>
  ),
}));

import { useAuth } from '@/features/auth';
import { useParams } from 'next/navigation';
import { getOrder, cancelOrder } from '@/entities/order';
import { getPayment } from '@/entities/payment';
import OrderDetailPage from '@/app/(store)/my/orders/[id]/page';

const mockUseAuth = vi.mocked(useAuth);
const mockUseParams = vi.mocked(useParams);
const mockGetOrder = vi.mocked(getOrder);
const mockCancelOrder = vi.mocked(cancelOrder);
const mockGetPayment = vi.mocked(getPayment);

const MOCK_ORDER: OrderDetail = {
  orderId: 'order-1',
  status: 'PENDING',
  totalPrice: 30000,
  items: [
    {
      productId: 'p1',
      variantId: 'v1',
      productName: '노트북',
      optionName: '실버',
      quantity: 1,
      unitPrice: 30000,
    },
  ],
  shippingAddress: {
    recipient: '홍길동',
    phone: '010-1234-5678',
    zipCode: '12345',
    address1: '서울시 강남구',
    address2: '101호',
  },
  createdAt: '2026-03-23T10:00:00Z',
  updatedAt: '2026-03-23T11:00:00Z',
};

describe('OrderDetailPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseAuth.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      user: null,
      login: vi.fn(),
      logout: vi.fn(),
    });
    mockUseParams.mockReturnValue({ id: 'order-1' });
    mockGetPayment.mockRejectedValue({ code: 'PAYMENT_NOT_FOUND', message: 'Not found' });
  });

  it('주문 상세 정보를 표시한다', async () => {
    mockGetOrder.mockResolvedValueOnce(MOCK_ORDER);

    render(<TestQueryProvider><OrderDetailPage /></TestQueryProvider>);

    await waitFor(() => {
      expect(screen.getByText('주문 상세')).toBeInTheDocument();
      expect(screen.getByText(/노트북/)).toBeInTheDocument();
      expect(screen.getAllByText(/30,000/).length).toBeGreaterThanOrEqual(1);
      expect(screen.getByText(/홍길동/)).toBeInTheDocument();
      expect(screen.getByText(/010-\*\*\*\*-5678/)).toBeInTheDocument();
    });
  });

  it('updatedAt을 표시한다', async () => {
    mockGetOrder.mockResolvedValueOnce(MOCK_ORDER);

    render(<TestQueryProvider><OrderDetailPage /></TestQueryProvider>);

    await waitFor(() => {
      expect(screen.getByText(/최종 수정일/)).toBeInTheDocument();
    });
  });

  it('items의 key가 productId-variantId 조합이다', async () => {
    const orderWith2Items: OrderDetail = {
      ...MOCK_ORDER,
      items: [
        { productId: 'p1', variantId: 'v1', productName: '노트북', optionName: '실버', quantity: 1, unitPrice: 30000 },
        { productId: 'p2', variantId: 'v2', productName: '마우스', optionName: '블랙', quantity: 2, unitPrice: 10000 },
      ],
    };
    mockGetOrder.mockResolvedValueOnce(orderWith2Items);

    render(<TestQueryProvider><OrderDetailPage /></TestQueryProvider>);

    await waitFor(() => {
      expect(screen.getByText(/노트북/)).toBeInTheDocument();
    });
    expect(screen.getByText(/마우스/)).toBeInTheDocument();
  });

  it('주문 상태 배지를 표시한다', async () => {
    mockGetOrder.mockResolvedValueOnce(MOCK_ORDER);

    render(<TestQueryProvider><OrderDetailPage /></TestQueryProvider>);

    await waitFor(() => {
      expect(screen.getByTestId('status-badge')).toHaveTextContent('PENDING');
    });
  });

  it('PENDING 상태에서 취소 버튼이 표시된다', async () => {
    mockGetOrder.mockResolvedValueOnce(MOCK_ORDER);

    render(<TestQueryProvider><OrderDetailPage /></TestQueryProvider>);

    await waitFor(() => {
      expect(screen.getByRole('button', { name: '주문 취소' })).toBeInTheDocument();
    });
  });

  it('CONFIRMED 상태에서도 취소 버튼이 표시된다', async () => {
    mockGetOrder.mockResolvedValueOnce({ ...MOCK_ORDER, status: 'CONFIRMED' });

    render(<TestQueryProvider><OrderDetailPage /></TestQueryProvider>);

    await waitFor(() => {
      expect(screen.getByRole('button', { name: '주문 취소' })).toBeInTheDocument();
    });
  });

  it('SHIPPED 상태에서 취소 버튼이 표시되지 않는다', async () => {
    mockGetOrder.mockResolvedValueOnce({ ...MOCK_ORDER, status: 'SHIPPED' });

    render(<TestQueryProvider><OrderDetailPage /></TestQueryProvider>);

    await waitFor(() => {
      expect(screen.getByText('주문 상세')).toBeInTheDocument();
    });
    expect(screen.queryByRole('button', { name: '주문 취소' })).not.toBeInTheDocument();
  });

  it('DELIVERED 상태에서 취소 버튼이 표시되지 않는다', async () => {
    mockGetOrder.mockResolvedValueOnce({ ...MOCK_ORDER, status: 'DELIVERED' });

    render(<TestQueryProvider><OrderDetailPage /></TestQueryProvider>);

    await waitFor(() => {
      expect(screen.getByText('주문 상세')).toBeInTheDocument();
    });
    expect(screen.queryByRole('button', { name: '주문 취소' })).not.toBeInTheDocument();
  });

  it('CANCELLED 상태에서 취소 버튼이 표시되지 않는다', async () => {
    mockGetOrder.mockResolvedValueOnce({ ...MOCK_ORDER, status: 'CANCELLED' });

    render(<TestQueryProvider><OrderDetailPage /></TestQueryProvider>);

    await waitFor(() => {
      expect(screen.getByText('주문 상세')).toBeInTheDocument();
    });
    expect(screen.queryByRole('button', { name: '주문 취소' })).not.toBeInTheDocument();
  });

  it('취소 성공 시 상태가 CANCELLED로 변경된다', async () => {
    mockGetOrder.mockResolvedValueOnce(MOCK_ORDER);
    mockCancelOrder.mockResolvedValueOnce({ orderId: 'order-1', status: 'CANCELLED' });

    const user = userEvent.setup();
    render(<TestQueryProvider><OrderDetailPage /></TestQueryProvider>);

    await waitFor(() => {
      expect(screen.getByRole('button', { name: '주문 취소' })).toBeInTheDocument();
    });

    await user.click(screen.getByRole('button', { name: '주문 취소' }));

    await waitFor(() => {
      expect(screen.getByTestId('status-badge')).toHaveTextContent('CANCELLED');
    });
    expect(screen.queryByRole('button', { name: '주문 취소' })).not.toBeInTheDocument();
  });

  it('취소 처리 중 버튼이 비활성화된다', async () => {
    mockGetOrder.mockResolvedValueOnce(MOCK_ORDER);

    let resolveCancelOrder: (value: CancelOrderResponse) => void;
    mockCancelOrder.mockImplementationOnce(
      () =>
        new Promise<CancelOrderResponse>((resolve) => {
          resolveCancelOrder = resolve;
        }),
    );

    const user = userEvent.setup();
    render(<TestQueryProvider><OrderDetailPage /></TestQueryProvider>);

    await waitFor(() => {
      expect(screen.getByRole('button', { name: '주문 취소' })).toBeInTheDocument();
    });

    await user.click(screen.getByRole('button', { name: '주문 취소' }));

    expect(screen.getByRole('button', { name: '취소 처리 중...' })).toBeDisabled();

    resolveCancelOrder!({ orderId: 'order-1', status: 'CANCELLED' });

    await waitFor(() => {
      expect(screen.getByTestId('status-badge')).toHaveTextContent('CANCELLED');
    });
  });

  it('취소 실패 시 API 에러 메시지를 표시한다', async () => {
    mockGetOrder.mockResolvedValueOnce(MOCK_ORDER);
    const apiError: ApiErrorResponse = {
      code: 'ORDER_CANNOT_BE_CANCELLED',
      message: '이미 배송된 주문은 취소할 수 없습니다.',
      timestamp: new Date().toISOString(),
    };
    mockCancelOrder.mockRejectedValueOnce(apiError);

    const user = userEvent.setup();
    render(<TestQueryProvider><OrderDetailPage /></TestQueryProvider>);

    await waitFor(() => {
      expect(screen.getByRole('button', { name: '주문 취소' })).toBeInTheDocument();
    });

    await user.click(screen.getByRole('button', { name: '주문 취소' }));

    await waitFor(() => {
      expect(screen.getByTestId('error-message')).toBeInTheDocument();
    });
    expect(screen.getByText('이미 배송된 주문은 취소할 수 없습니다.')).toBeInTheDocument();
  });

  it('알 수 없는 에러 시 기본 취소 실패 메시지를 표시한다', async () => {
    mockGetOrder.mockResolvedValueOnce(MOCK_ORDER);
    mockCancelOrder.mockRejectedValueOnce(new Error('unknown'));

    const user = userEvent.setup();
    render(<TestQueryProvider><OrderDetailPage /></TestQueryProvider>);

    await waitFor(() => {
      expect(screen.getByRole('button', { name: '주문 취소' })).toBeInTheDocument();
    });

    await user.click(screen.getByRole('button', { name: '주문 취소' }));

    await waitFor(() => {
      expect(screen.getByTestId('error-message')).toBeInTheDocument();
    });
    expect(screen.getByText('주문 취소에 실패했습니다.')).toBeInTheDocument();
  });

  it('ORDER_NOT_FOUND 에러 시 전용 메시지를 표시한다', async () => {
    const apiError: ApiErrorResponse = {
      code: 'ORDER_NOT_FOUND',
      message: 'Order not found',
      timestamp: new Date().toISOString(),
    };
    mockGetOrder.mockRejectedValueOnce(apiError);

    render(<TestQueryProvider><OrderDetailPage /></TestQueryProvider>);

    await waitFor(() => {
      expect(screen.getByTestId('error-message')).toBeInTheDocument();
    });
    expect(screen.getByText('주문을 찾을 수 없습니다.')).toBeInTheDocument();
  });

  it('일반 로드 에러 시 기본 메시지를 표시한다', async () => {
    mockGetOrder.mockRejectedValueOnce(new Error('network error'));

    render(<TestQueryProvider><OrderDetailPage /></TestQueryProvider>);

    await waitFor(() => {
      expect(screen.getByTestId('error-message')).toBeInTheDocument();
    });
    expect(screen.getByText('주문 정보를 불러오는데 실패했습니다.')).toBeInTheDocument();
  });

  it('로딩 중일 때 주문 내용이 표시되지 않는다', () => {
    mockGetOrder.mockReturnValue(new Promise(() => {}));

    render(<TestQueryProvider><OrderDetailPage /></TestQueryProvider>);

    expect(screen.queryByText('주문 상세')).not.toBeInTheDocument();
    expect(screen.queryByText('주문 상품')).not.toBeInTheDocument();
  });

  describe('결제 정보', () => {
    const MOCK_PAYMENT: PaymentResponse = {
      paymentId: 'pay-1',
      orderId: 'order-1',
      userId: 'user-1',
      amount: 30000,
      status: 'COMPLETED',
      paymentMethod: 'CARD',
      paymentKey: 'toss_pk_test_123',
      receiptUrl: null,
      createdAt: '2026-03-23T10:00:00Z',
      paidAt: '2026-03-23T10:01:00Z',
      refundedAt: null,
    };

    it('결제 정보가 있으면 결제 섹션을 표시한다', async () => {
      mockGetOrder.mockResolvedValueOnce(MOCK_ORDER);
      mockGetPayment.mockResolvedValueOnce(MOCK_PAYMENT);

      render(<TestQueryProvider><OrderDetailPage /></TestQueryProvider>);

      await waitFor(() => {
        expect(screen.getByText('결제 정보')).toBeInTheDocument();
      });
      expect(screen.getByTestId('payment-status-badge')).toHaveTextContent('COMPLETED');
      expect(screen.getByText(/결제 금액:/)).toBeInTheDocument();
    });

    it('결제일을 표시한다', async () => {
      mockGetOrder.mockResolvedValueOnce(MOCK_ORDER);
      mockGetPayment.mockResolvedValueOnce(MOCK_PAYMENT);

      render(<TestQueryProvider><OrderDetailPage /></TestQueryProvider>);

      await waitFor(() => {
        expect(screen.getByText(/결제일:/)).toBeInTheDocument();
      });
    });

    it('환불일이 있으면 환불일을 표시한다', async () => {
      mockGetOrder.mockResolvedValueOnce(MOCK_ORDER);
      mockGetPayment.mockResolvedValueOnce({
        ...MOCK_PAYMENT,
        status: 'REFUNDED',
        refundedAt: '2026-03-24T10:00:00Z',
      });

      render(<TestQueryProvider><OrderDetailPage /></TestQueryProvider>);

      await waitFor(() => {
        expect(screen.getByText(/환불일:/)).toBeInTheDocument();
      });
    });

    it('paidAt이 null이면 결제일을 표시하지 않는다', async () => {
      mockGetOrder.mockResolvedValueOnce(MOCK_ORDER);
      mockGetPayment.mockResolvedValueOnce({
        ...MOCK_PAYMENT,
        status: 'PENDING',
        paidAt: null,
      });

      render(<TestQueryProvider><OrderDetailPage /></TestQueryProvider>);

      await waitFor(() => {
        expect(screen.getByText('결제 정보')).toBeInTheDocument();
      });
      expect(screen.queryByText(/결제일:/)).not.toBeInTheDocument();
    });

    it('결제 정보가 없으면(404) 결제 섹션이 표시되지 않는다', async () => {
      mockGetOrder.mockResolvedValueOnce(MOCK_ORDER);
      mockGetPayment.mockRejectedValueOnce({ code: 'PAYMENT_NOT_FOUND', message: 'Not found' });

      render(<TestQueryProvider><OrderDetailPage /></TestQueryProvider>);

      await waitFor(() => {
        expect(screen.getByText('주문 상세')).toBeInTheDocument();
      });
      expect(screen.queryByText('결제 정보')).not.toBeInTheDocument();
    });

    it('네트워크 오류 시 결제 에러 메시지를 표시한다', async () => {
      mockGetOrder.mockResolvedValueOnce(MOCK_ORDER);
      mockGetPayment.mockRejectedValueOnce(new Error('network error'));

      render(<TestQueryProvider><OrderDetailPage /></TestQueryProvider>);

      await waitFor(() => {
        expect(screen.getByText('결제 정보를 불러오는데 실패했습니다.')).toBeInTheDocument();
      });
    });
  });
});
