'use client';

import { useState } from 'react';
import { ApiError, messageForCode } from '@/shared/api/errors';
import {
  useAddVariant,
  useUpdateVariant,
  useDeleteVariant,
} from './use-ecommerce-products';
import type { Variant } from '../api/types';

/**
 * Inline variant CRUD state + mutations for {@link VariantEditor}
 * (TASK-PC-FE-142 — extracted from the former fat container, behavior-preserving).
 *
 * Owns the inline-edit / add / delete-confirm state slices and the three
 * mutations (add / update / delete). Each mutation invalidates the product
 * detail query (via the hooks). Stock is NOT edited here (UpdateVariantRequest
 * has no stock field — that's the separate StockAdjustDialog surface). Logic is
 * 1:1 with the pre-split file; the table / add-row render the returned slices.
 * (`variants` stays a {@link VariantTable} prop — the hook owns no list state.)
 */
export function useVariantEditor(productId: string) {
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

  function cancelEdit() {
    setEditId(null);
    setRowError(null);
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

  function openDelete(v: Variant) {
    setDelError(null);
    setToDelete(v);
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

  function cancelDelete() {
    setToDelete(null);
    setDelError(null);
  }

  return {
    edit: {
      editId,
      editName,
      setEditName,
      editAddPrice,
      setEditAddPrice,
      rowError,
      pending: update.isPending,
      startEdit,
      saveEdit,
      cancelEdit,
    },
    openDelete,
    add: {
      addName,
      setAddName,
      addStock,
      setAddStock,
      addPrice,
      setAddPrice,
      addError,
      pending: add.isPending,
      submitAdd,
    },
    del: {
      toDelete,
      delError,
      pending: del.isPending,
      confirmDelete,
      cancelDelete,
      description: toDelete ? `"${toDelete.optionName}" 옵션을 삭제합니다.` : '',
    },
  };
}
