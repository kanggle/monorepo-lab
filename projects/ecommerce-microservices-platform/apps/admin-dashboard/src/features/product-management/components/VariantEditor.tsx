'use client';

import { useRef } from 'react';

export interface VariantInput {
  _key: number;
  optionName: string;
  stock: number;
  additionalPrice: number;
}

interface VariantEditorProps {
  variants: VariantInput[];
  onChange: (variants: VariantInput[]) => void;
  initialKeyCount: number;
}

const styles = {
  section: { marginBottom: '24px' } as const,
  header: { display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '12px' } as const,
  title: { fontSize: '1.125rem', fontWeight: 600, margin: 0 } as const,
  addBtn: { padding: '4px 12px', fontSize: '0.75rem', border: '1px solid #d1d5db', borderRadius: '4px', backgroundColor: '#fff', cursor: 'pointer' } as const,
  row: { display: 'flex', gap: '8px', alignItems: 'end', marginBottom: '8px' } as const,
  label: { display: 'block', marginBottom: '4px', fontSize: '0.75rem' } as const,
  input: { padding: '8px 12px', border: '1px solid #d1d5db', borderRadius: '6px' } as const,
  numberInput: { padding: '8px 12px', border: '1px solid #d1d5db', borderRadius: '6px', width: '100px' } as const,
  priceInput: { padding: '8px 12px', border: '1px solid #d1d5db', borderRadius: '6px', width: '120px' } as const,
  removeBtn: { padding: '8px 12px', border: '1px solid #ccc', borderRadius: '6px', color: '#333', backgroundColor: '#fff', cursor: 'pointer' } as const,
};

export function VariantEditor({ variants, onChange, initialKeyCount }: VariantEditorProps) {
  const keyRef = useRef(initialKeyCount);

  function addVariant() {
    const key = keyRef.current++;
    onChange([...variants, { _key: key, optionName: '', stock: 0, additionalPrice: 0 }]);
  }

  function removeVariant(index: number) {
    onChange(variants.filter((_, i) => i !== index));
  }

  function updateVariant(index: number, field: Exclude<keyof VariantInput, '_key'>, value: string | number) {
    onChange(variants.map((v, i) => (i === index ? { ...v, [field]: value } : v)));
  }

  return (
    <section style={styles.section}>
      <div style={styles.header}>
        <h2 style={styles.title}>옵션</h2>
        <button type="button" onClick={addVariant} style={styles.addBtn}>+ 옵션 추가</button>
      </div>
      {variants.map((variant, index) => (
        <div key={variant._key} style={styles.row}>
          <div>
            <label htmlFor={`variant-optionName-${variant._key}`} style={styles.label}>옵션명</label>
            <input id={`variant-optionName-${variant._key}`} type="text" value={variant.optionName} onChange={(e) => updateVariant(index, 'optionName', e.target.value)} style={styles.input} />
          </div>
          <div>
            <label htmlFor={`variant-stock-${variant._key}`} style={styles.label}>재고</label>
            <input id={`variant-stock-${variant._key}`} type="number" value={variant.stock} onChange={(e) => updateVariant(index, 'stock', Number(e.target.value))} min={0} style={styles.numberInput} />
          </div>
          <div>
            <label htmlFor={`variant-additionalPrice-${variant._key}`} style={styles.label}>추가 가격</label>
            <input id={`variant-additionalPrice-${variant._key}`} type="number" value={variant.additionalPrice} onChange={(e) => updateVariant(index, 'additionalPrice', Number(e.target.value))} min={0} style={styles.priceInput} />
          </div>
          {variants.length > 1 && (
            <button type="button" onClick={() => removeVariant(index)} aria-label={`옵션 ${variant.optionName || index + 1} 삭제`} style={styles.removeBtn}>삭제</button>
          )}
        </div>
      ))}
    </section>
  );
}
