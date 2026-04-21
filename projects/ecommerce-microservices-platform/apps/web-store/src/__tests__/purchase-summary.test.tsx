import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { PurchaseSummary } from '@/widgets/product-detail-with-cart/PurchaseSummary';

describe('PurchaseSummary', () => {
  const defaultProps = {
    totalPrice: 25000,
    totalQuantity: 2,
    canAdd: true,
    onAddToCart: vi.fn(),
    onBuyNow: vi.fn(),
  };

  it('총 금액을 천 단위 구분 기호와 함께 표시한다', () => {
    render(<PurchaseSummary {...defaultProps} totalPrice={123456} />);

    expect(screen.getByText('123,456')).toBeInTheDocument();
  });

  it('totalQuantity가 0보다 크면 개수를 라벨에 표시한다', () => {
    render(<PurchaseSummary {...defaultProps} totalQuantity={3} />);

    expect(screen.getByText('총 금액 (3개)')).toBeInTheDocument();
  });

  it('totalQuantity가 0이면 개수를 표시하지 않는다', () => {
    render(<PurchaseSummary {...defaultProps} totalQuantity={0} canAdd={false} />);

    expect(screen.getByText('총 금액')).toBeInTheDocument();
  });

  it('canAdd=true이면 장바구니 담기 버튼 텍스트를 표시한다', () => {
    render(<PurchaseSummary {...defaultProps} canAdd={true} />);

    expect(screen.getByRole('button', { name: '장바구니 담기' })).toBeInTheDocument();
  });

  it('canAdd=false이면 옵션 선택 안내 텍스트와 버튼 비활성화 상태를 표시한다', () => {
    render(<PurchaseSummary {...defaultProps} canAdd={false} />);

    expect(screen.getByRole('button', { name: '옵션을 선택하세요' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '즉시 주문' })).toBeDisabled();
  });

  it('장바구니 담기 버튼 클릭 시 onAddToCart를 호출한다', () => {
    const onAddToCart = vi.fn();
    render(<PurchaseSummary {...defaultProps} onAddToCart={onAddToCart} />);

    fireEvent.click(screen.getByRole('button', { name: '장바구니 담기' }));

    expect(onAddToCart).toHaveBeenCalledTimes(1);
  });

  it('즉시 주문 버튼 클릭 시 onBuyNow를 호출한다', () => {
    const onBuyNow = vi.fn();
    render(<PurchaseSummary {...defaultProps} onBuyNow={onBuyNow} />);

    fireEvent.click(screen.getByRole('button', { name: '즉시 주문' }));

    expect(onBuyNow).toHaveBeenCalledTimes(1);
  });
});
