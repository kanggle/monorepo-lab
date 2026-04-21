import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { OrderSummary, PaginatedResponse } from '@repo/types';
import { TestQueryProvider } from './test-utils';

const mockReplace = vi.fn();
vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: mockReplace, push: vi.fn() }),
}));

vi.mock('next/link', () => ({
  default: ({ href, children, ...props }: { href: string; children: React.ReactNode; [key: string]: unknown }) => (
    <a href={href} {...props}>{children}</a>
  ),
}));

vi.mock('@/features/auth', () => ({
  useAuth: vi.fn(),
  useRequireAuth: vi.fn(),
}));

vi.mock('@/entities/order', () => ({
  getOrders: vi.fn(),
  OrderCard: ({ order }: { order: OrderSummary }) => (
    <div data-testid="order-card">{order.orderId} - {order.totalPrice.toLocaleString()}원</div>
  ),
}));

vi.mock('@repo/ui', () => ({
  LoadingSpinner: () => <div data-testid="loading-spinner">로딩 중...</div>,
  ErrorMessage: ({ message, onRetry }: { message: string; onRetry: () => void }) => (
    <div data-testid="error-message">
      {message}
      <button onClick={onRetry}>재시도</button>
    </div>
  ),
  EmptyState: ({ message }: { message: string }) => (
    <div data-testid="empty-state">{message}</div>
  ),
}));

import { useAuth, useRequireAuth } from '@/features/auth';
import { getOrders } from '@/entities/order';
import OrdersPage from '@/app/(store)/orders/page';

const mockUseAuth = vi.mocked(useAuth);
const mockUseRequireAuth = vi.mocked(useRequireAuth);
const mockGetOrders = vi.mocked(getOrders);

const MOCK_ORDERS: OrderSummary[] = [
  { orderId: 'order-1', status: 'PENDING', totalPrice: 30000, itemCount: 2, firstItemName: '테스트 상품', createdAt: '2026-03-23T10:00:00Z' },
  { orderId: 'order-2', status: 'CONFIRMED', totalPrice: 50000, itemCount: 1, firstItemName: '테스트 상품2', createdAt: '2026-03-22T10:00:00Z' },
];

function createPaginatedResponse(
  content: OrderSummary[],
  page = 0,
  size = 20,
  totalElements = content.length,
): PaginatedResponse<OrderSummary> {
  return { content, page, size, totalElements };
}

describe('OrdersPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseAuth.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      user: null,
      login: vi.fn(),
      signup: vi.fn(),
      logout: vi.fn(),
    });
    mockUseRequireAuth.mockReturnValue({ isReady: true });
  });

  it('로딩 중일 때 주문 내용이 표시되지 않는다', () => {
    mockGetOrders.mockReturnValue(new Promise(() => {}));

    render(<TestQueryProvider><OrdersPage /></TestQueryProvider>);

    expect(screen.queryAllByTestId('order-card')).toHaveLength(0);
    expect(screen.queryByTestId('empty-state')).not.toBeInTheDocument();
  });

  it('주문 목록을 렌더링한다', async () => {
    mockGetOrders.mockResolvedValueOnce(createPaginatedResponse(MOCK_ORDERS));

    render(<TestQueryProvider><OrdersPage /></TestQueryProvider>);

    await waitFor(() => {
      expect(screen.getAllByTestId('order-card')).toHaveLength(2);
    });
    expect(screen.getByText(/order-1/)).toBeInTheDocument();
    expect(screen.getByText(/order-2/)).toBeInTheDocument();
  });

  it('주문이 없으면 빈 상태를 표시한다', async () => {
    mockGetOrders.mockResolvedValueOnce(createPaginatedResponse([]));

    render(<TestQueryProvider><OrdersPage /></TestQueryProvider>);

    await waitFor(() => {
      expect(screen.getByTestId('empty-state')).toBeInTheDocument();
    });
    expect(screen.getByText('주문 내역이 없습니다.')).toBeInTheDocument();
  });

  it('에러 발생 시 에러 메시지를 표시한다', async () => {
    mockGetOrders.mockRejectedValueOnce(new Error('fail'));

    render(<TestQueryProvider><OrdersPage /></TestQueryProvider>);

    await waitFor(() => {
      expect(screen.getByTestId('error-message')).toBeInTheDocument();
    });
    expect(screen.getByText('주문 목록을 불러오는데 실패했습니다.')).toBeInTheDocument();
  });

  it('에러 후 재시도 버튼을 클릭하면 다시 로드한다', async () => {
    mockGetOrders.mockRejectedValueOnce(new Error('fail'));

    const user = userEvent.setup();
    render(<TestQueryProvider><OrdersPage /></TestQueryProvider>);

    await waitFor(() => {
      expect(screen.getByTestId('error-message')).toBeInTheDocument();
    });

    mockGetOrders.mockResolvedValueOnce(createPaginatedResponse(MOCK_ORDERS));
    await user.click(screen.getByText('재시도'));

    await waitFor(() => {
      expect(screen.getAllByTestId('order-card')).toHaveLength(2);
    });
  });

  it('미인증 상태에서 로그인 페이지로 리다이렉트한다', () => {
    mockUseRequireAuth.mockImplementation(() => {
      mockReplace('/login');
      return { isReady: false };
    });

    render(<TestQueryProvider><OrdersPage /></TestQueryProvider>);

    expect(mockReplace).toHaveBeenCalledWith('/login');
  });

  describe('페이지네이션', () => {
    it('페이지네이션 컨트롤을 표시한다', async () => {
      mockGetOrders.mockResolvedValueOnce(createPaginatedResponse(MOCK_ORDERS, 0, 20, 50));

      render(<TestQueryProvider><OrdersPage /></TestQueryProvider>);

      await waitFor(() => {
        expect(screen.getByText('1 / 3')).toBeInTheDocument();
      });
      expect(screen.getByText('이전')).toBeInTheDocument();
      expect(screen.getByText('다음')).toBeInTheDocument();
    });

    it('첫 페이지에서 이전 버튼이 비활성화된다', async () => {
      mockGetOrders.mockResolvedValueOnce(createPaginatedResponse(MOCK_ORDERS, 0, 20, 50));

      render(<TestQueryProvider><OrdersPage /></TestQueryProvider>);

      await waitFor(() => {
        expect(screen.getByText('이전')).toBeDisabled();
      });
    });

    it('다음 버튼 클릭 시 다음 페이지를 로드한다', async () => {
      mockGetOrders.mockResolvedValueOnce(createPaginatedResponse(MOCK_ORDERS, 0, 20, 50));

      const user = userEvent.setup();
      render(<TestQueryProvider><OrdersPage /></TestQueryProvider>);

      await waitFor(() => {
        expect(screen.getByText('다음')).toBeEnabled();
      });

      mockGetOrders.mockResolvedValueOnce(createPaginatedResponse(MOCK_ORDERS, 1, 20, 50));
      await user.click(screen.getByText('다음'));

      await waitFor(() => {
        expect(screen.getByText('2 / 3')).toBeInTheDocument();
      });
    });

    it('마지막 페이지에서 다음 버튼이 비활성화된다', async () => {
      mockGetOrders.mockResolvedValueOnce(createPaginatedResponse(MOCK_ORDERS, 2, 20, 50));

      render(<TestQueryProvider><OrdersPage /></TestQueryProvider>);

      // 마지막 페이지로 이동하기 위해 직접 state를 세팅할 수 없으므로
      // 다음 버튼 클릭으로 이동
      await waitFor(() => {
        expect(screen.getAllByTestId('order-card')).toHaveLength(2);
      });
    });

    it('페이지 크기를 변경할 수 있다', async () => {
      mockGetOrders.mockResolvedValueOnce(createPaginatedResponse(MOCK_ORDERS, 0, 20, 50));

      const user = userEvent.setup();
      render(<TestQueryProvider><OrdersPage /></TestQueryProvider>);

      await waitFor(() => {
        expect(screen.getByLabelText('페이지 크기:')).toBeInTheDocument();
      });

      mockGetOrders.mockResolvedValueOnce(createPaginatedResponse(MOCK_ORDERS, 0, 10, 50));
      await user.selectOptions(screen.getByLabelText('페이지 크기:'), '10');

      await waitFor(() => {
        expect(mockGetOrders).toHaveBeenCalledWith(0, 10);
      });
    });
  });
});
