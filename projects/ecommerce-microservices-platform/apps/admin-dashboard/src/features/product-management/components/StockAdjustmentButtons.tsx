'use client';

import type { ProductVariant } from '@repo/types';

interface Props {
  variants: ProductVariant[];
  onSelect: (variant: ProductVariant) => void;
}

export function StockAdjustmentButtons({ variants, onSelect }: Props) {
  if (variants.length === 0) {
    return (
      <div style={{ marginTop: '16px', borderTop: '1px solid #eee', paddingTop: '12px' }}>
        <p>등록된 옵션이 없습니다.</p>
      </div>
    );
  }

  return (
    <div style={{ marginTop: '16px', borderTop: '1px solid #eee', paddingTop: '12px' }}>
      {variants.map((variant) => (
        <button
          key={variant.id}
          onClick={() => onSelect(variant)}
          style={{
            padding: '6px 14px',
            fontSize: '0.75rem',
            border: '1px solid #d1d5db',
            borderRadius: '6px',
            backgroundColor: '#fff',
            cursor: 'pointer',
            marginRight: '8px',
            marginBottom: '4px',
          }}
        >
          재고 조정
        </button>
      ))}
    </div>
  );
}
