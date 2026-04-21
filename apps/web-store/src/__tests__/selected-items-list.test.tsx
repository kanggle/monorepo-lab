import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import type { ProductVariant } from '@repo/types';
import { SelectedItemsList } from '@/widgets/product-detail-with-cart/SelectedItemsList';

const V1: ProductVariant = { id: 'v1', optionName: '옵션A', stock: 10, additionalPrice: 0 };
const V2: ProductVariant = { id: 'v2', optionName: '옵션B', stock: 5, additionalPrice: 2000 };

function buildMap() {
  return new Map<string, ProductVariant>([
    ['v1', V1],
    ['v2', V2],
  ]);
}

describe('SelectedItemsList', () => {
  it('선택된 항목이 없으면 아무것도 렌더링하지 않는다', () => {
    const { container } = render(
      <SelectedItemsList
        selectedItems={[]}
        variantMap={buildMap()}
        basePrice={10000}
        onQuantityChange={vi.fn()}
        onRemove={vi.fn()}
      />,
    );

    expect(container.firstChild).toBeNull();
  });

  it('선택된 항목의 옵션명과 수량, 단가 * 수량을 표시한다', () => {
    render(
      <SelectedItemsList
        selectedItems={[{ variantId: 'v2', quantity: 3 }]}
        variantMap={buildMap()}
        basePrice={10000}
        onQuantityChange={vi.fn()}
        onRemove={vi.fn()}
      />,
    );

    expect(screen.getByText('옵션B')).toBeInTheDocument();
    expect(screen.getByText('3')).toBeInTheDocument();
    expect(screen.getByText('36,000원')).toBeInTheDocument();
  });

  it('수량이 1이면 감소 버튼이 비활성화된다', () => {
    render(
      <SelectedItemsList
        selectedItems={[{ variantId: 'v1', quantity: 1 }]}
        variantMap={buildMap()}
        basePrice={10000}
        onQuantityChange={vi.fn()}
        onRemove={vi.fn()}
      />,
    );

    expect(screen.getByRole('button', { name: '수량 줄이기' })).toBeDisabled();
  });

  it('수량이 재고와 같으면 증가 버튼이 비활성화된다', () => {
    render(
      <SelectedItemsList
        selectedItems={[{ variantId: 'v2', quantity: 5 }]}
        variantMap={buildMap()}
        basePrice={10000}
        onQuantityChange={vi.fn()}
        onRemove={vi.fn()}
      />,
    );

    expect(screen.getByRole('button', { name: '수량 늘리기' })).toBeDisabled();
  });

  it('수량 감소 버튼 클릭 시 onQuantityChange를 현재 수량 - 1로 호출한다', () => {
    const onQuantityChange = vi.fn();
    render(
      <SelectedItemsList
        selectedItems={[{ variantId: 'v1', quantity: 3 }]}
        variantMap={buildMap()}
        basePrice={10000}
        onQuantityChange={onQuantityChange}
        onRemove={vi.fn()}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: '수량 줄이기' }));

    expect(onQuantityChange).toHaveBeenCalledWith('v1', 2);
  });

  it('수량 증가 버튼 클릭 시 onQuantityChange를 현재 수량 + 1로 호출한다', () => {
    const onQuantityChange = vi.fn();
    render(
      <SelectedItemsList
        selectedItems={[{ variantId: 'v1', quantity: 3 }]}
        variantMap={buildMap()}
        basePrice={10000}
        onQuantityChange={onQuantityChange}
        onRemove={vi.fn()}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: '수량 늘리기' }));

    expect(onQuantityChange).toHaveBeenCalledWith('v1', 4);
  });

  it('삭제 버튼 클릭 시 onRemove를 호출한다', () => {
    const onRemove = vi.fn();
    render(
      <SelectedItemsList
        selectedItems={[{ variantId: 'v2', quantity: 1 }]}
        variantMap={buildMap()}
        basePrice={10000}
        onQuantityChange={vi.fn()}
        onRemove={onRemove}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: '옵션B 삭제' }));

    expect(onRemove).toHaveBeenCalledWith('v2');
  });

  it('variantMap에 없는 variantId는 렌더링되지 않는다', () => {
    render(
      <SelectedItemsList
        selectedItems={[{ variantId: 'unknown', quantity: 1 }]}
        variantMap={buildMap()}
        basePrice={10000}
        onQuantityChange={vi.fn()}
        onRemove={vi.fn()}
      />,
    );

    expect(screen.queryByText('옵션A')).not.toBeInTheDocument();
    expect(screen.queryByText('옵션B')).not.toBeInTheDocument();
  });
});
