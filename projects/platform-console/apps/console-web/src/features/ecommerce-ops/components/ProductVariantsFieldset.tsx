'use client';

import { Button } from '@/shared/ui/Button';
import type { VariantDraft } from '../hooks/use-product-form';

interface ProductVariantsFieldsetProps {
  variants: VariantDraft[];
  setVariant: (i: number, patch: Partial<VariantDraft>) => void;
  addVariantRow: () => void;
  removeVariantRow: (i: number) => void;
  inputCls: string;
}

/**
 * Register-mode variant editor (TASK-PC-FE-139 — extracted from {@link ProductForm},
 * presentational only). Renders the option rows (옵션명 / 재고 / 추가 가격) with the
 * persistent column header and add/remove controls. test-ids unchanged so the
 * existing register-mode unit/e2e assertions keep passing (TASK-PC-FE-130).
 */
export function ProductVariantsFieldset({
  variants,
  setVariant,
  addVariantRow,
  removeVariantRow,
  inputCls,
}: ProductVariantsFieldsetProps) {
  return (
    <fieldset className="rounded-md border border-border p-4" data-testid="product-form-variants">
      <legend className="px-1 text-sm font-medium text-foreground">
        옵션(variant) <span className="text-destructive">*</span>
      </legend>
      <p className="mb-3 text-xs text-muted-foreground">
        상품 등록에는 최소 1개의 옵션이 필요합니다. 재고·추가 가격을 비워두면 0으로 등록됩니다.
      </p>
      <div
        className="mb-1 hidden gap-2 px-1 text-xs font-medium text-muted-foreground sm:grid sm:grid-cols-[1fr_6rem_8rem_auto]"
        data-testid="product-form-variant-header"
        aria-hidden="true"
      >
        <span>옵션명</span>
        <span>재고</span>
        <span>추가 가격</span>
        <span />
      </div>
      <div className="space-y-3">
        {variants.map((v, i) => (
          <div
            key={i}
            className="grid grid-cols-1 gap-2 sm:grid-cols-[1fr_6rem_8rem_auto]"
            data-testid={`product-form-variant-${i}`}
          >
            <input
              aria-label={`옵션명 ${i + 1}`}
              placeholder="옵션명"
              value={v.optionName}
              onChange={(e) => setVariant(i, { optionName: e.target.value })}
              className={inputCls}
              data-testid={`product-form-variant-name-${i}`}
            />
            <input
              aria-label={`재고 ${i + 1}`}
              inputMode="numeric"
              placeholder="재고"
              value={v.stock}
              onChange={(e) =>
                setVariant(i, { stock: e.target.value.replace(/[^0-9]/g, '') })
              }
              className={inputCls}
              data-testid={`product-form-variant-stock-${i}`}
            />
            <input
              aria-label={`추가 가격 ${i + 1}`}
              inputMode="numeric"
              placeholder="추가 가격"
              value={v.additionalPrice}
              onChange={(e) =>
                setVariant(i, {
                  additionalPrice: e.target.value.replace(/[^0-9]/g, ''),
                })
              }
              className={inputCls}
              data-testid={`product-form-variant-addprice-${i}`}
            />
            <Button
              type="button"
              variant="secondary"
              size="sm"
              onClick={() => removeVariantRow(i)}
              disabled={variants.length <= 1}
              data-testid={`product-form-variant-remove-${i}`}
            >
              삭제
            </Button>
          </div>
        ))}
      </div>
      <Button
        type="button"
        variant="secondary"
        size="sm"
        onClick={addVariantRow}
        className="mt-3"
        data-testid="product-form-variant-add"
      >
        옵션 추가
      </Button>
    </fieldset>
  );
}
