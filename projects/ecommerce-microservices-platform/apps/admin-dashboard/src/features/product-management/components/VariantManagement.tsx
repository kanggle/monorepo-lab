'use client';

import type { ProductVariant } from '@repo/types';
import { useVariantManagement } from '../hooks/use-variant-management';
import { VariantViewRow } from './VariantViewRow';
import { VariantEditRow } from './VariantEditRow';
import { VariantAddRow } from './VariantAddRow';

interface Props {
  productId: string;
  variants: ProductVariant[];
  onChanged: () => void;
}

export function VariantManagement({ productId, variants, onChanged }: Props) {
  const {
    editing,
    setEditing,
    adding,
    setAdding,
    error,
    isMutating,
    handleUpdate,
    handleDelete,
    handleAdd,
  } = useVariantManagement(productId, onChanged);

  return (
    <div>
      {error && (
        <div style={{ color: '#333', backgroundColor: '#f5f5f5', border: '1px solid #ddd', borderRadius: '8px', padding: '8px 12px', marginBottom: '12px', fontSize: '0.8125rem' }}>
          {error}
        </div>
      )}

      <table style={{ width: '100%', borderCollapse: 'collapse' }}>
        <thead>
          <tr style={{ backgroundColor: '#f9fafb' }}>
            <th style={{ padding: '10px 16px', textAlign: 'left', fontSize: '0.8125rem', color: '#6b7280', fontWeight: 600 }}>옵션명</th>
            <th style={{ padding: '10px 16px', textAlign: 'left', fontSize: '0.8125rem', color: '#6b7280', fontWeight: 600 }}>추가 가격</th>
            <th style={{ padding: '10px 16px', textAlign: 'left', fontSize: '0.8125rem', color: '#6b7280', fontWeight: 600 }}>재고</th>
            <th style={{ padding: '10px 16px', textAlign: 'right', fontSize: '0.8125rem', color: '#6b7280', fontWeight: 600 }}>작업</th>
          </tr>
        </thead>
        <tbody>
          {variants.map((v) => (
            <tr key={v.id} style={{ borderBottom: '1px solid #f3f4f6' }}>
              {editing?.variantId === v.id ? (
                <VariantEditRow
                  editing={editing}
                  variant={v}
                  isMutating={isMutating}
                  onEditChange={setEditing}
                  onSave={handleUpdate}
                  onCancel={() => setEditing(null)}
                />
              ) : (
                <VariantViewRow
                  variant={v}
                  showDelete={variants.length > 1}
                  isMutating={isMutating}
                  onEdit={setEditing}
                  onDelete={handleDelete}
                />
              )}
            </tr>
          ))}

          {adding && (
            <VariantAddRow
              adding={adding}
              isMutating={isMutating}
              onAddChange={setAdding}
              onAdd={handleAdd}
              onCancel={() => setAdding(null)}
            />
          )}
        </tbody>
      </table>

      {!adding && (
        <button
          onClick={() => setAdding({ optionName: '', stock: 0, additionalPrice: 0 })}
          style={{
            marginTop: '12px',
            padding: '8px 16px',
            fontSize: '0.8125rem',
            border: '1px dashed #d1d5db',
            borderRadius: '8px',
            backgroundColor: '#fff',
            cursor: 'pointer',
            color: '#6b7280',
            width: '100%',
          }}
        >
          + 옵션 추가
        </button>
      )}
    </div>
  );
}
