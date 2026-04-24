import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { OrderStatusBadge } from '@/entities/order/ui/OrderStatusBadge';
import type { OrderStatus } from '@repo/types';

describe('OrderStatusBadge', () => {
  const statusLabelMap: [OrderStatus, string][] = [
    ['PENDING', '주문 대기'],
    ['CONFIRMED', '주문 확인'],
    ['SHIPPED', '배송 중'],
    ['DELIVERED', '배송 완료'],
    ['CANCELLED', '취소됨'],
  ];

  it.each(statusLabelMap)(
    '%s 상태를 올바른 한국어 라벨(%s)로 표시한다',
    (status, label) => {
      render(<OrderStatusBadge status={status} />);
      expect(screen.getByText(label)).toBeInTheDocument();
    },
  );

  it('PENDING 상태의 배지가 span 요소로 렌더링된다', () => {
    const { container } = render(<OrderStatusBadge status="PENDING" />);
    const badge = container.querySelector('span');
    expect(badge).not.toBeNull();
    expect(badge?.textContent).toBe('주문 대기');
  });

  it('DELIVERED 상태와 CANCELLED 상태가 서로 다른 배경색을 가진다', () => {
    const { unmount } = render(<OrderStatusBadge status="DELIVERED" />);
    const deliveredBadge = screen.getByText('배송 완료');
    const deliveredBg = deliveredBadge.style.backgroundColor;
    unmount();

    render(<OrderStatusBadge status="CANCELLED" />);
    const cancelledBadge = screen.getByText('취소됨');
    const cancelledBg = cancelledBadge.style.backgroundColor;

    expect(deliveredBg).not.toBe(cancelledBg);
  });

  it('모든 상태에 대해 배경색이 설정되어 있다', () => {
    for (const [status, label] of statusLabelMap) {
      const { unmount } = render(<OrderStatusBadge status={status} />);
      const badge = screen.getByText(label);
      expect(badge.style.backgroundColor).toBeTruthy();
      unmount();
    }
  });
});
