'use client';

import type { AddingState } from './variant-management-types';

export function VariantAddRow({
  adding, isMutating, onAddChange, onAdd, onCancel,
}: {
  adding: AddingState;
  isMutating: boolean;
  onAddChange: (state: AddingState) => void;
  onAdd: () => void;
  onCancel: () => void;
}) {
  return (
    <tr style={{ borderBottom: '1px solid #f3f4f6', backgroundColor: '#fafafa' }}>
      <td style={{ padding: '8px 16px' }}>
        <input
          value={adding.optionName}
          onChange={(e) => onAddChange({ ...adding, optionName: e.target.value })}
          placeholder="옵션명"
          style={{ padding: '6px 10px', border: '1px solid #d1d5db', borderRadius: '6px', fontSize: '0.875rem', width: '100%', boxSizing: 'border-box' as const }}
        />
      </td>
      <td style={{ padding: '8px 16px' }}>
        <input
          type="number"
          value={adding.additionalPrice}
          onChange={(e) => onAddChange({ ...adding, additionalPrice: Number(e.target.value) })}
          min={0}
          style={{ padding: '6px 10px', border: '1px solid #d1d5db', borderRadius: '6px', fontSize: '0.875rem', width: '100px' }}
        />
      </td>
      <td style={{ padding: '8px 16px' }}>
        <input
          type="number"
          value={adding.stock}
          onChange={(e) => onAddChange({ ...adding, stock: Number(e.target.value) })}
          min={0}
          style={{ padding: '6px 10px', border: '1px solid #d1d5db', borderRadius: '6px', fontSize: '0.875rem', width: '80px' }}
        />
      </td>
      <td style={{ padding: '8px 16px', textAlign: 'right' }}>
        <div style={{ display: 'flex', gap: '6px', justifyContent: 'flex-end' }}>
          <button onClick={onAdd} disabled={isMutating || !adding.optionName.trim()}
            style={{ padding: '5px 12px', fontSize: '0.75rem', border: 'none', borderRadius: '6px', backgroundColor: '#1A1A2E', color: '#fff', cursor: 'pointer', fontWeight: 500 }}>
            추가
          </button>
          <button onClick={onCancel}
            style={{ padding: '5px 12px', fontSize: '0.75rem', border: '1px solid #d1d5db', borderRadius: '6px', backgroundColor: '#fff', cursor: 'pointer' }}>
            취소
          </button>
        </div>
      </td>
    </tr>
  );
}
