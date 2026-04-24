import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import type { OrderSummary } from '@repo/types';

vi.mock('next/link', () => ({
  default: ({ href, children, ...props }: { href: string; children: React.ReactNode; [key: string]: unknown }) => (
    <a href={href} {...props}>{children}</a>
  ),
}));

import { OrderCard } from '@/entities/order/ui/OrderCard';

describe('OrderCard', () => {
  const baseOrder: OrderSummary = {
    orderId: 'order-123',
    status: 'CONFIRMED',
    totalPrice: 58000,
    itemCount: 3,
    firstItemName: '클래식 화이트 티셔츠',
    createdAt: '2026-04-01T09:30:00Z',
  };

  it('주문 상세 페이지로의 링크를 렌더링한다', () => {
    render(<OrderCard order={baseOrder} />);

    const link = screen.getByRole('link');
    expect(link).toHaveAttribute('href', '/my/orders/order-123');
  });

  it('주문 생성 날짜를 한국어 형식으로 표시한다', () => {
    render(<OrderCard order={baseOrder} />);

    expect(screen.getByText(/2026/)).toBeInTheDocument();
  });

  it('첫 번째 상품명과 나머지 건수를 표시한다', () => {
    render(<OrderCard order={baseOrder} />);

    expect(screen.getByText('클래식 화이트 티셔츠 외 2건')).toBeInTheDocument();
  });

  it('상품이 1건일 때 "외 N건" 없이 상품명만 표시한다', () => {
    const singleItemOrder: OrderSummary = {
      ...baseOrder,
      itemCount: 1,
    };
    render(<OrderCard order={singleItemOrder} />);

    expect(screen.getByText('클래식 화이트 티셔츠')).toBeInTheDocument();
    expect(screen.queryByText(/외/)).not.toBeInTheDocument();
  });

  it('주문 총 금액을 원화 형식으로 표시한다', () => {
    render(<OrderCard order={baseOrder} />);

    expect(screen.getByText(/58,000원/)).toBeInTheDocument();
  });

  it('주문 상태 배지를 표시한다', () => {
    render(<OrderCard order={baseOrder} />);

    expect(screen.getByText('주문 확인')).toBeInTheDocument();
  });

  it('firstItemName이 없으면 상품명 영역을 렌더링하지 않는다', () => {
    const noNameOrder: OrderSummary = {
      ...baseOrder,
      firstItemName: undefined as unknown as string,
    };
    render(<OrderCard order={noNameOrder} />);

    expect(screen.queryByText(/외/)).not.toBeInTheDocument();
    // 금액과 상태는 여전히 표시
    expect(screen.getByText(/58,000원/)).toBeInTheDocument();
  });

  it('CANCELLED 상태의 주문도 정상적으로 렌더링한다', () => {
    const cancelledOrder: OrderSummary = {
      ...baseOrder,
      status: 'CANCELLED',
    };
    render(<OrderCard order={cancelledOrder} />);

    expect(screen.getByText('취소됨')).toBeInTheDocument();
    expect(screen.getByRole('link')).toHaveAttribute('href', '/my/orders/order-123');
  });
});
