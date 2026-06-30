'use client';

import type { Dispatch, SetStateAction } from 'react';
import { Button } from '@/shared/ui/Button';

interface VariantAddSlice {
  addName: string;
  setAddName: Dispatch<SetStateAction<string>>;
  addStock: string;
  setAddStock: Dispatch<SetStateAction<string>>;
  addPrice: string;
  setAddPrice: Dispatch<SetStateAction<string>>;
  addError: string | null;
  pending: boolean;
  submitAdd: () => void;
}

interface VariantAddRowProps {
  add: VariantAddSlice;
  inputCls: string;
}

/**
 * Inline "add variant" row (TASK-PC-FE-142 — extracted from {@link VariantEditor},
 * presentational only). State stays owned by `useVariantEditor`; all
 * `data-testid`s are unchanged.
 */
export function VariantAddRow({ add, inputCls }: VariantAddRowProps) {
  return (
    <div className="rounded-md border border-dashed border-border p-3" data-testid="variant-add-row">
      <p className="mb-2 text-sm font-medium text-foreground">옵션 추가</p>
      <div className="grid grid-cols-1 gap-2 sm:grid-cols-[1fr_6rem_8rem_auto]">
        <input
          aria-label="새 옵션명"
          placeholder="옵션명"
          value={add.addName}
          onChange={(e) => add.setAddName(e.target.value)}
          className={inputCls}
          data-testid="variant-add-name"
        />
        <input
          aria-label="새 옵션 재고"
          inputMode="numeric"
          placeholder="재고"
          value={add.addStock}
          onChange={(e) => add.setAddStock(e.target.value.replace(/[^0-9]/g, ''))}
          className={inputCls}
          data-testid="variant-add-stock"
        />
        <input
          aria-label="새 옵션 추가 가격"
          inputMode="numeric"
          placeholder="추가 가격"
          value={add.addPrice}
          onChange={(e) => add.setAddPrice(e.target.value.replace(/[^0-9]/g, ''))}
          className={inputCls}
          data-testid="variant-add-addprice"
        />
        <Button
          size="sm"
          onClick={add.submitAdd}
          disabled={add.pending}
          data-testid="variant-add-submit"
        >
          추가
        </Button>
      </div>
      {add.addError && (
        <p role="alert" className="mt-2 text-xs text-destructive" data-testid="variant-add-error">
          {add.addError}
        </p>
      )}
    </div>
  );
}
