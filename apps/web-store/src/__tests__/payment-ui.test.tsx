import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { PaymentStatusBadge } from '@/entities/payment/ui/PaymentStatusBadge';

describe('PaymentStatusBadge', () => {
  it.each([
    ['PENDING' as const, '결제 대기'],
    ['COMPLETED' as const, '결제 완료'],
    ['FAILED' as const, '결제 실패'],
    ['REFUNDED' as const, '환불 완료'],
  ])('%s 상태를 올바른 라벨로 표시한다', (status, label) => {
    render(<PaymentStatusBadge status={status} />);
    expect(screen.getByText(label)).toBeInTheDocument();
  });
});
