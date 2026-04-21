import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { OrderStatusBadge } from '@/entities/order/ui/OrderStatusBadge';
import { OrderCard } from '@/entities/order/ui/OrderCard';
import type { OrderSummary } from '@repo/types';

vi.mock('next/link', () => ({
  default: ({ href, children, ...props }: { href: string; children: React.ReactNode; [key: string]: unknown }) => (
    <a href={href} {...props}>{children}</a>
  ),
}));

describe('OrderStatusBadge', () => {
  it.each([
    ['PENDING' as const, '주문 대기'],
    ['CONFIRMED' as const, '주문 확인'],
    ['SHIPPED' as const, '배송 중'],
    ['DELIVERED' as const, '배송 완료'],
    ['CANCELLED' as const, '취소됨'],
  ])('%s 상태를 올바른 라벨로 표시한다', (status, label) => {
    render(<OrderStatusBadge status={status} />);
    expect(screen.getByText(label)).toBeInTheDocument();
  });
});

describe('OrderCard', () => {
  const order: OrderSummary = {
    orderId: 'order-1',
    status: 'CONFIRMED',
    totalPrice: 50000,
    itemCount: 2,
    firstItemName: '테스트 상품',
    createdAt: '2026-03-23T10:00:00Z',
  };

  it('주문 금액과 상품 수를 표시한다', () => {
    render(<OrderCard order={order} />);

    expect(screen.getByText(/50,000원/)).toBeInTheDocument();
    expect(screen.getByText(/테스트 상품 외 1건/)).toBeInTheDocument();
  });

  it('주문 상태를 표시한다', () => {
    render(<OrderCard order={order} />);

    expect(screen.getByText('주문 확인')).toBeInTheDocument();
  });

  it('주문 상세 페이지로 링크한다', () => {
    render(<OrderCard order={order} />);

    const link = screen.getByRole('link');
    expect(link).toHaveAttribute('href', '/my/orders/order-1');
  });

  it('주문 날짜를 표시한다', () => {
    render(<OrderCard order={order} />);

    // 날짜 형식은 로케일에 따라 다를 수 있음
    expect(screen.getByText(/2026/)).toBeInTheDocument();
  });
});
