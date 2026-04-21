'use client';

import type { ProductVariant } from '@repo/types';
import type { EditingState } from './variant-management-types';

export function VariantEditRow({
  editing, variant, isMutating, onEditChange, onSave, onCancel,
}: {
  editing: EditingState;
  variant: ProductVariant;
  isMutating: boolean;
  onEditChange: (state: EditingState) => void;
  onSave: () => void;
  onCancel: () => void;
}) {
  return (
    <>
      <td style={{ padding: '8px 16px' }}>
        <input
          value={editing.optionName}
          onChange={(e) => onEditChange({ ...editing, optionName: e.target.value })}
          style={{ padding: '6px 10px', border: '1px solid #d1d5db', borderRadius: '6px', fontSize: '0.875rem', width: '100%', boxSizing: 'border-box' as const }}
        />
      </td>
      <td style={{ padding: '8px 16px' }}>
        <input
          type="number"
          value={editing.additionalPrice}
          onChange={(e) => onEditChange({ ...editing, additionalPrice: Number(e.target.value) })}
          min={0}
          style={{ padding: '6px 10px', border: '1px solid #d1d5db', borderRadius: '6px', fontSize: '0.875rem', width: '100px' }}
        />
      </td>
      <td style={{ padding: '8px 16px', fontSize: '0.875rem', color: '#6b7280' }}>{variant.stock}</td>
      <td style={{ padding: '8px 16px', textAlign: 'right' }}>
        <div style={{ display: 'flex', gap: '6px', justifyContent: 'flex-end' }}>
          <button onClick={onSave} disabled={isMutating}
            style={{ padding: '5px 12px', fontSize: '0.75rem', border: 'none', borderRadius: '6px', backgroundColor: '#1A1A2E', color: '#fff', cursor: 'pointer', fontWeight: 500 }}>
            저장
          </button>
          <button onClick={onCancel}
            style={{ padding: '5px 12px', fontSize: '0.75rem', border: '1px solid #d1d5db', borderRadius: '6px', backgroundColor: '#fff', cursor: 'pointer' }}>
            취소
          </button>
        </div>
      </td>
    </>
  );
}
