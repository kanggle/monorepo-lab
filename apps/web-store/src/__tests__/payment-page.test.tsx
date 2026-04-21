import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { TestQueryProvider } from './test-utils';

const mockPush = vi.fn();
const mockReplace = vi.fn();
const mockSearchParams = new URLSearchParams();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush, replace: mockReplace }),
  useSearchParams: () => mockSearchParams,
}));

vi.mock('@/features/auth', () => ({
  useRequireAuth: () => ({ isReady: true }),
}));

vi.mock('@/entities/payment', () => ({
  confirmPayment: vi.fn(),
}));

vi.mock('next/link', () => ({
  default: ({ href, children, ...props }: { href: string; children: React.ReactNode }) => (
    <a href={href} {...props}>{children}</a>
  ),
}));

import { confirmPayment } from '@/entities/payment';
import PaymentSuccessPage from '@/app/(store)/checkout/payment/success/page';
import PaymentFailPage from '@/app/(store)/checkout/payment/fail/page';

const mockConfirmPayment = vi.mocked(confirmPayment);

function setSearchParams(params: Record<string, string>) {
  // Clear all existing params
  Array.from(mockSearchParams.keys()).forEach((key) => mockSearchParams.delete(key));
  Object.entries(params).forEach(([key, value]) => {
    mockSearchParams.set(key, value);
  });
}

describe('PaymentSuccessPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setSearchParams({});
  });

  it('confirm API 성공 시 /checkout/complete 로 이동한다', async () => {
    mockConfirmPayment.mockResolvedValueOnce({
      paymentId: 'pay-1',
      orderId: 'order-1',
      status: 'COMPLETED',
    });

    setSearchParams({
      paymentKey: 'pk_test_123',
      orderId: 'order-1',
      amount: '30000',
    });

    render(<TestQueryProvider><PaymentSuccessPage /></TestQueryProvider>);

    await waitFor(() => {
      expect(mockConfirmPayment).toHaveBeenCalledWith({
        paymentKey: 'pk_test_123',
        orderId: 'order-1',
        amount: 30000,
      });
    });

    await waitFor(() => {
      expect(mockReplace).toHaveBeenCalledWith('/checkout/complete?orderId=order-1');
    });
  });

  it('confirm API 실패 시 에러 메시지 및 재시도 버튼을 표시한다', async () => {
    mockConfirmPayment.mockRejectedValueOnce({
      message: '결제 승인에 실패했습니다.',
    });

    setSearchParams({
      paymentKey: 'pk_test_123',
      orderId: 'order-1',
      amount: '30000',
    });

    render(<TestQueryProvider><PaymentSuccessPage /></TestQueryProvider>);

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('결제 승인에 실패했습니다.');
    });

    expect(screen.getByRole('button', { name: /다시 시도/ })).toBeInTheDocument();
  });
});

describe('PaymentFailPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setSearchParams({});
  });

  it('에러 코드와 메시지를 표시한다', () => {
    setSearchParams({
      code: 'PAY_PROCESS_CANCELED',
      message: '사용자가 결제를 취소했습니다.',
    });

    render(<PaymentFailPage />);

    expect(screen.getByText(/PAY_PROCESS_CANCELED/)).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('사용자가 결제를 취소했습니다.');
  });

  it('orderId가 있을 때 재시도 링크를 표시한다', () => {
    setSearchParams({
      code: 'PAY_PROCESS_CANCELED',
      message: '취소됨',
      orderId: 'order-1',
    });

    render(<PaymentFailPage />);

    const retryLink = screen.getByRole('link', { name: /다시 시도/ });
    expect(retryLink).toBeInTheDocument();
    expect(retryLink).toHaveAttribute('href', '/checkout/payment?orderId=order-1');
  });
});
