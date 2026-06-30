'use client';

import type { Dispatch, SetStateAction } from 'react';
import { Button } from '@/shared/ui/Button';
import type { Variant } from '../api/types';

interface VariantEditSlice {
  editId: string | null;
  editName: string;
  setEditName: Dispatch<SetStateAction<string>>;
  editAddPrice: string;
  setEditAddPrice: Dispatch<SetStateAction<string>>;
  rowError: string | null;
  pending: boolean;
  startEdit: (v: Variant) => void;
  saveEdit: () => void;
  cancelEdit: () => void;
}

interface VariantTableProps {
  variants: Variant[];
  edit: VariantEditSlice;
  openDelete: (v: Variant) => void;
  inputCls: string;
}

/**
 * Product variant table with in-place row editing (TASK-PC-FE-142 — extracted
 * from {@link VariantEditor}, presentational only). Stock is read-only here.
 * Edit/add/delete state stays owned by `useVariantEditor`; all `data-testid`s
 * are unchanged.
 */
export function VariantTable({ variants, edit, openDelete, inputCls }: VariantTableProps) {
  return (
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
          const editing = edit.editId === v.id;
          return (
            <tr key={v.id} data-testid={`variant-row-${i}`} className="border-b border-border align-top">
              <td className="p-2">
                {editing ? (
                  <input
                    aria-label="옵션명"
                    value={edit.editName}
                    onChange={(e) => edit.setEditName(e.target.value)}
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
                    value={edit.editAddPrice}
                    onChange={(e) => edit.setEditAddPrice(e.target.value.replace(/[^0-9]/g, ''))}
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
                        onClick={edit.saveEdit}
                        disabled={edit.pending}
                        data-testid={`variant-save-${i}`}
                      >
                        저장
                      </Button>
                      <Button
                        size="sm"
                        variant="secondary"
                        onClick={edit.cancelEdit}
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
                        onClick={() => edit.startEdit(v)}
                        data-testid={`variant-editbtn-${i}`}
                      >
                        수정
                      </Button>
                      <Button
                        size="sm"
                        variant="secondary"
                        onClick={() => openDelete(v)}
                        disabled={variants.length <= 1}
                        title={variants.length <= 1 ? '마지막 옵션은 삭제할 수 없습니다.' : undefined}
                        data-testid={`variant-delete-${i}`}
                      >
                        삭제
                      </Button>
                    </>
                  )}
                </div>
                {editing && edit.rowError && (
                  <p role="alert" className="mt-1 text-xs text-destructive" data-testid={`variant-row-error-${i}`}>
                    {edit.rowError}
                  </p>
                )}
              </td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}
