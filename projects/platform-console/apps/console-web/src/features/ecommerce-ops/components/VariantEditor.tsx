'use client';

import { useState } from 'react';
import { Button } from '@/shared/ui/Button';
import { ApiError, messageForCode } from '@/shared/api/errors';
import {
  useAddVariant,
  useUpdateVariant,
  useDeleteVariant,
} from '../hooks/use-ecommerce-products';
import type { Variant } from '../api/types';
import { ConfirmDialog } from './ConfirmDialog';

/**
 * Inline variant CRUD for a product detail (TASK-PC-FE-081 — § 2.4.10
 * #6 add / #7 update / #8 delete). Add is an inline row; update is in-place;
 * delete is confirm-gated. Stock is NOT edited here (UpdateVariantRequest has
 * no stock field — stock is the separate adjust surface, StockAdjustDialog).
 *
 * Each mutation invalidates the product detail query so the variant table
 * reflects the new state.
 */
export interface VariantEditorProps {
  productId: string;
  variants: Variant[];
}

const inputCls =
  'w-full rounded-md border border-border bg-background px-2 py-1 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary';

export function VariantEditor({ productId, variants }: VariantEditorProps) {
  const add = useAddVariant();
  const update = useUpdateVariant();
  const del = useDeleteVariant();

  // --- inline edit state ---------------------------------------------------
  const [editId, setEditId] = useState<string | null>(null);
  const [editName, setEditName] = useState('');
  const [editAddPrice, setEditAddPrice] = useState('');
  const [rowError, setRowError] = useState<string | null>(null);

  // --- add state -----------------------------------------------------------
  const [addName, setAddName] = useState('');
  const [addStock, setAddStock] = useState('');
  const [addPrice, setAddPrice] = useState('');
  const [addError, setAddError] = useState<string | null>(null);

  // --- delete confirm ------------------------------------------------------
  const [toDelete, setToDelete] = useState<Variant | null>(null);
  const [delError, setDelError] = useState<string | null>(null);

  function errMsg(e: unknown, fallback: string) {
    const code = e instanceof ApiError ? e.code : 'SERVICE_UNAVAILABLE';
    return messageForCode(code, fallback);
  }

  function startEdit(v: Variant) {
    setRowError(null);
    setEditId(v.id);
    setEditName(v.optionName);
    setEditAddPrice(String(v.additionalPrice));
  }

  function saveEdit() {
    if (editId === null) return;
    const addPriceNum = Number(editAddPrice);
    if (editName.trim() === '' || !Number.isInteger(addPriceNum) || addPriceNum < 0) {
      setRowError('옵션명과 추가 가격(0 이상)을 확인하세요.');
      return;
    }
    update.mutate(
      {
        productId,
        variantId: editId,
        body: { optionName: editName.trim(), additionalPrice: addPriceNum },
      },
      {
        onSuccess: () => {
          setEditId(null);
          setRowError(null);
        },
        onError: (e) => setRowError(errMsg(e, '옵션을 수정하지 못했습니다.')),
      },
    );
  }

  function submitAdd() {
    const stockNum = Number(addStock);
    const priceNum = Number(addPrice);
    if (
      addName.trim() === '' ||
      !Number.isInteger(stockNum) ||
      stockNum < 0 ||
      !Number.isInteger(priceNum) ||
      priceNum < 0
    ) {
      setAddError('옵션명 · 재고(0 이상) · 추가 가격(0 이상)을 확인하세요.');
      return;
    }
    setAddError(null);
    add.mutate(
      {
        productId,
        body: { optionName: addName.trim(), stock: stockNum, additionalPrice: priceNum },
      },
      {
        onSuccess: () => {
          setAddName('');
          setAddStock('');
          setAddPrice('');
        },
        onError: (e) => setAddError(errMsg(e, '옵션을 추가하지 못했습니다.')),
      },
    );
  }

  function confirmDelete() {
    if (!toDelete) return;
    setDelError(null);
    del.mutate(
      { productId, variantId: toDelete.id },
      {
        onSuccess: () => setToDelete(null),
        onError: (e) => setDelError(errMsg(e, '옵션을 삭제하지 못했습니다.')),
      },
    );
  }

  return (
    <div data-testid="variant-editor">
      <h3 className="mb-2 text-base font-medium text-foreground">옵션(variant)</h3>
      <table className="mb-3 data-table" data-testid="variant-table">
        <caption className="sr-only">상품 옵션 목록</caption>
        <thead>
          <tr className="border-b border-border text-left">
            <th scope="col" className="p-2">옵션명</th>
            <th scope="col" className="p-2">재고</th>
            <th scope="col" className="p-2">추가 가격</th>
            <th scope="col" className="p-2">작업</th>
          </tr>
        </thead>
        <tbody>
          {variants.map((v, i) => {
            const editing = editId === v.id;
            return (
              <tr key={v.id} data-testid={`variant-row-${i}`} className="border-b border-border align-top">
                <td className="p-2">
                  {editing ? (
                    <input
                      aria-label="옵션명"
                      value={editName}
                      onChange={(e) => setEditName(e.target.value)}
                      className={inputCls}
                      data-testid={`variant-edit-name-${i}`}
                    />
                  ) : (
                    v.optionName
                  )}
                </td>
                <td className="p-2" data-testid={`variant-stock-${i}`}>{v.stock}</td>
                <td className="p-2">
                  {editing ? (
                    <input
                      aria-label="추가 가격"
                      inputMode="numeric"
                      value={editAddPrice}
                      onChange={(e) => setEditAddPrice(e.target.value.replace(/[^0-9]/g, ''))}
                      className={inputCls}
                      data-testid={`variant-edit-addprice-${i}`}
                    />
                  ) : (
                    v.additionalPrice.toLocaleString('ko-KR')
                  )}
                </td>
                <td className="p-2">
                  <div className="flex flex-wrap gap-2">
                    {editing ? (
                      <>
                        <Button
                          size="sm"
                          onClick={saveEdit}
                          disabled={update.isPending}
                          data-testid={`variant-save-${i}`}
                        >
                          저장
                        </Button>
                        <Button
                          size="sm"
                          variant="secondary"
                          onClick={() => {
                            setEditId(null);
                            setRowError(null);
                          }}
                          data-testid={`variant-cancel-${i}`}
                        >
                          취소
                        </Button>
                      </>
                    ) : (
                      <>
                        <Button
                          size="sm"
                          variant="secondary"
                          onClick={() => startEdit(v)}
                          data-testid={`variant-editbtn-${i}`}
                        >
                          수정
                        </Button>
                        <Button
                          size="sm"
                          variant="secondary"
                          onClick={() => {
                            setDelError(null);
                            setToDelete(v);
                          }}
                          disabled={variants.length <= 1}
                          title={variants.length <= 1 ? '마지막 옵션은 삭제할 수 없습니다.' : undefined}
                          data-testid={`variant-delete-${i}`}
                        >
                          삭제
                        </Button>
                      </>
                    )}
                  </div>
                  {editing && rowError && (
                    <p role="alert" className="mt-1 text-xs text-destructive" data-testid={`variant-row-error-${i}`}>
                      {rowError}
                    </p>
                  )}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>

      {/* Add variant inline row */}
      <div className="rounded-md border border-dashed border-border p-3" data-testid="variant-add-row">
        <p className="mb-2 text-sm font-medium text-foreground">옵션 추가</p>
        <div className="grid grid-cols-1 gap-2 sm:grid-cols-[1fr_6rem_8rem_auto]">
          <input
            aria-label="새 옵션명"
            placeholder="옵션명"
            value={addName}
            onChange={(e) => setAddName(e.target.value)}
            className={inputCls}
            data-testid="variant-add-name"
          />
          <input
            aria-label="새 옵션 재고"
            inputMode="numeric"
            placeholder="재고"
            value={addStock}
            onChange={(e) => setAddStock(e.target.value.replace(/[^0-9]/g, ''))}
            className={inputCls}
            data-testid="variant-add-stock"
          />
          <input
            aria-label="새 옵션 추가 가격"
            inputMode="numeric"
            placeholder="추가 가격"
            value={addPrice}
            onChange={(e) => setAddPrice(e.target.value.replace(/[^0-9]/g, ''))}
            className={inputCls}
            data-testid="variant-add-addprice"
          />
          <Button
            size="sm"
            onClick={submitAdd}
            disabled={add.isPending}
            data-testid="variant-add-submit"
          >
            추가
          </Button>
        </div>
        {addError && (
          <p role="alert" className="mt-2 text-xs text-destructive" data-testid="variant-add-error">
            {addError}
          </p>
        )}
      </div>

      <ConfirmDialog
        open={toDelete !== null}
        title="옵션을 삭제할까요?"
        description={
          toDelete ? `"${toDelete.optionName}" 옵션을 삭제합니다.` : ''
        }
        confirmLabel="삭제"
        tone="destructive"
        pending={del.isPending}
        errorMessage={delError}
        onConfirm={confirmDelete}
        onCancel={() => {
          setToDelete(null);
          setDelError(null);
        }}
      />
    </div>
  );
}
