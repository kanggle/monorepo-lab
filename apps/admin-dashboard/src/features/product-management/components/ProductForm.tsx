'use client';

import { useRouter } from 'next/navigation';
import type { ProductDetail, ProductStatus } from '@repo/types';
import { useProductForm } from '../hooks/use-product-form';
import { VariantEditor } from './VariantEditor';
import { ProductImageSection } from './ProductImageSection';
import { Section } from '@/shared/ui';
import { formStyles } from '@/shared/lib/form-styles';

interface Props {
  product?: ProductDetail;
}

const styles = {
  ...formStyles,
  layout: {
    display: 'flex',
    gap: '24px',
    alignItems: 'flex-start',
  } as const,
  imageColumn: {
    flexShrink: 0,
    width: '280px',
  } as const,
  formColumn: {
    flex: 1,
    minWidth: 0,
  } as const,
  fieldGrid: { display: 'grid', gap: '12px', maxWidth: '480px' } as const,
  textarea: { width: '100%', padding: '8px 12px', border: '1px solid #d1d5db', borderRadius: '6px', resize: 'vertical' } as const,
  categoryInputEditing: { width: '100%', padding: '8px 12px', border: '1px solid #d1d5db', borderRadius: '6px', backgroundColor: '#f3f4f6' } as const,
  categoryInputCreating: { width: '100%', padding: '8px 12px', border: '1px solid #d1d5db', borderRadius: '6px', backgroundColor: '#fff' } as const,
};

export function ProductForm({ product }: Props) {
  const router = useRouter();
  const {
    name, setName,
    description, setDescription,
    price, setPrice,
    categoryId, setCategoryId,
    status, setStatus,
    variants, setVariants,
    error,
    isSubmitting,
    isEdit,
    isValid,
    handleSubmit,
  } = useProductForm(product);

  return (
    <form onSubmit={handleSubmit}>
      {error && <p role="alert" style={styles.error}>{error}</p>}

      <Section title="기본 정보">
        <div style={styles.layout}>
          <div style={styles.imageColumn}>
            <ProductImageSection productId={product?.id} thumbnailUrl={product?.thumbnailUrl} />
          </div>

          <div style={styles.formColumn}>
            <div style={styles.fieldGrid}>
              <div>
                <label htmlFor="name" style={styles.label}>상품명 *</label>
                <input id="name" type="text" value={name} onChange={(e) => setName(e.target.value)} required style={styles.input} />
              </div>
              <div>
                <label htmlFor="description" style={styles.label}>설명</label>
                <textarea id="description" value={description} onChange={(e) => setDescription(e.target.value)} rows={4} style={styles.textarea} />
              </div>
              <div>
                <label htmlFor="price" style={styles.label}>가격 *</label>
                <input id="price" type="number" value={price} onChange={(e) => setPrice(Number(e.target.value))} min={0} required style={styles.input} />
              </div>
              <div>
                <label htmlFor="categoryId" style={styles.label}>카테고리 ID *</label>
                <input id="categoryId" type="text" value={categoryId}
                  onChange={(e) => setCategoryId(e.target.value)}
                  disabled={isEdit}
                  style={isEdit ? styles.categoryInputEditing : styles.categoryInputCreating} />
              </div>
              {isEdit && (
                <div>
                  <label htmlFor="status" style={styles.label}>상태</label>
                  <select id="status" value={status} onChange={(e) => setStatus(e.target.value as ProductStatus)} style={styles.input}>
                    <option value="ON_SALE">판매중</option>
                    <option value="SOLD_OUT">품절</option>
                    <option value="HIDDEN">숨김</option>
                  </select>
                </div>
              )}
            </div>
          </div>
        </div>
      </Section>

      {!isEdit && (
        <VariantEditor variants={variants} onChange={setVariants} initialKeyCount={variants.length} />
      )}

      <div style={styles.buttonRow}>
        <button type="submit" disabled={!isValid || isSubmitting}
          style={isValid ? styles.submitBtn : styles.submitBtnDisabled}>
          {isSubmitting ? '저장 중...' : isEdit ? '수정' : '등록'}
        </button>
        <button type="button" onClick={() => router.back()} style={styles.cancelBtn}>취소</button>
      </div>
    </form>
  );
}
