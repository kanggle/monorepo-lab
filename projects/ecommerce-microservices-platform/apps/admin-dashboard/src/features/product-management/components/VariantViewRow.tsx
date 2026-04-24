'use client';

import type { ProductVariant } from '@repo/types';
import type { EditingState } from './variant-management-types';

export function VariantViewRow({
  variant, showDelete, isMutating, onEdit, onDelete,
}: {
  variant: ProductVariant;
  showDelete: boolean;
  isMutating: boolean;
  onEdit: (state: EditingState) => void;
  onDelete: (variantId: string) => void;
}) {
  return (
    <>
      <td style={{ padding: '10px 16px', fontSize: '0.875rem' }}>{variant.optionName}</td>
      <td style={{ padding: '10px 16px', fontSize: '0.875rem' }}>
        {variant.additionalPrice > 0 ? `+${variant.additionalPrice.toLocaleString()}원` : '-'}
      </td>
      <td style={{ padding: '10px 16px', fontSize: '0.875rem' }}>{variant.stock}</td>
      <td style={{ padding: '10px 16px', textAlign: 'right' }}>
        <div style={{ display: 'flex', gap: '6px', justifyContent: 'flex-end' }}>
          <button
            onClick={() => onEdit({ variantId: variant.id, optionName: variant.optionName, additionalPrice: variant.additionalPrice })}
            style={{ padding: '5px 12px', fontSize: '0.75rem', border: '1px solid #d1d5db', borderRadius: '6px', backgroundColor: '#fff', cursor: 'pointer' }}>
            수정
          </button>
          {showDelete && (
            <button
              onClick={() => onDelete(variant.id)}
              disabled={isMutating}
              style={{ padding: '5px 12px', fontSize: '0.75rem', border: '1px solid #ccc', borderRadius: '6px', backgroundColor: '#fff', color: '#333', cursor: 'pointer' }}>
              삭제
            </button>
          )}
        </div>
      </td>
    </>
  );
}
