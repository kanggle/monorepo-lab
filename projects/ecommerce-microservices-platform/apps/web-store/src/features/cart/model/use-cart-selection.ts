import { useState } from 'react';
import type { CartItem } from './types';

function itemKey(item: CartItem): string {
  return `${item.productId}-${item.variantId}`;
}

export function useCartSelection(items: CartItem[]) {
  const [checkedSet, setCheckedSet] = useState<Set<string>>(
    () => new Set(items.map(itemKey)),
  );

  const allChecked = items.length > 0 && items.every((i) => checkedSet.has(itemKey(i)));
  const checkedItems = items.filter((i) => checkedSet.has(itemKey(i)));
  const totalAmount = checkedItems.reduce((sum, i) => sum + i.price * i.quantity, 0);

  function toggleAll() {
    if (allChecked) {
      setCheckedSet(new Set());
    } else {
      setCheckedSet(new Set(items.map(itemKey)));
    }
  }

  function toggleItem(key: string) {
    setCheckedSet((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  }

  function isChecked(item: CartItem): boolean {
    return checkedSet.has(itemKey(item));
  }

  function clearSelection() {
    setCheckedSet(new Set());
  }

  return {
    allChecked,
    checkedItems,
    totalAmount,
    toggleAll,
    toggleItem,
    isChecked,
    clearSelection,
    itemKey,
  };
}
