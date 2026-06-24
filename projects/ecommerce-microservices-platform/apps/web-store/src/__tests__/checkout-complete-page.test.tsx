import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from '@testing-library/react';

let mockParams = new URLSearchParams();
vi.mock('next/navigation', () => ({
  useSearchParams: () => mockParams,
}));

const mockClearCart = vi.fn();
vi.mock('@/features/cart', () => ({
  useCart: () => ({ clearCart: mockClearCart }),
}));

const mockClearKey = vi.fn();
vi.mock('@/features/checkout', () => ({
  clearCheckoutIdempotencyKey: () => mockClearKey(),
}));

import CheckoutCompletePage from '@/app/(store)/checkout/complete/page';

describe('CheckoutCompletePage (TASK-BE-430)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('결제 완료(orderId 존재) 시 카트와 멱등키를 비운다', () => {
    mockParams = new URLSearchParams({ orderId: 'order-1' });

    render(<CheckoutCompletePage />);

    expect(mockClearCart).toHaveBeenCalledTimes(1);
    expect(mockClearKey).toHaveBeenCalledTimes(1);
  });

  it('orderId가 없으면 카트를 비우지 않는다', () => {
    mockParams = new URLSearchParams();

    render(<CheckoutCompletePage />);

    expect(mockClearCart).not.toHaveBeenCalled();
    expect(mockClearKey).not.toHaveBeenCalled();
  });
});
