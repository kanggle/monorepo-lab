'use client';

import { useCallback, useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import type { ProductDetail } from '@repo/types';
import { useCart } from '@/features/cart';
import type { SelectedItem } from './types';

export interface ProductVariantSelectionResult {
  selectedItems: SelectedItem[];
  dropdownOpen: boolean;
  variantMap: Map<string, ProductDetail['variants'][number]>;
  totalQuantity: number;
  totalPrice: number;
  canAdd: boolean;
  showToast: boolean;
  handleSelect: (variantId: string) => void;
  handleRemove: (variantId: string) => void;
  handleQuantity: (variantId: string, next: number) => void;
  handleDropdownToggle: () => void;
  handleDropdownClose: () => void;
  handleAddToCart: () => void;
  handleBuyNow: () => void;
  clearToast: () => void;
}

export function useProductVariantSelection(product: ProductDetail): ProductVariantSelectionResult {
  const router = useRouter();
  const { addItem } = useCart();

  const [selectedItems, setSelectedItems] = useState<SelectedItem[]>([]);
  const [showToast, setShowToast] = useState(false);
  const [dropdownOpen, setDropdownOpen] = useState(false);

  const variantMap = useMemo(
    () => new Map(product.variants.map((v) => [v.id, v])),
    [product.variants],
  );

  function handleSelect(variantId: string) {
    if (!variantId) return;
    if (selectedItems.some((s) => s.variantId === variantId)) return;
    const variant = variantMap.get(variantId);
    if (!variant || variant.stock === 0) return;
    setSelectedItems((prev) => [...prev, { variantId, quantity: 1 }]);
    setDropdownOpen(false);
  }

  function handleRemove(variantId: string) {
    setSelectedItems((prev) => prev.filter((s) => s.variantId !== variantId));
  }

  function handleQuantity(variantId: string, next: number) {
    const variant = variantMap.get(variantId);
    if (!variant) return;
    const clamped = Math.max(1, Math.min(variant.stock, next));
    setSelectedItems((prev) =>
      prev.map((s) => (s.variantId === variantId ? { ...s, quantity: clamped } : s)),
    );
  }

  const totalQuantity = useMemo(
    () => selectedItems.reduce((sum, s) => sum + s.quantity, 0),
    [selectedItems],
  );

  const totalPrice = useMemo(
    () =>
      selectedItems.reduce((sum, s) => {
        const v = variantMap.get(s.variantId);
        if (!v) return sum;
        return sum + (product.price + v.additionalPrice) * s.quantity;
      }, 0),
    [selectedItems, variantMap, product.price],
  );

  const canAdd = selectedItems.length > 0;

  const addSelectedItemsToCart = useCallback(() => {
    for (const item of selectedItems) {
      const v = variantMap.get(item.variantId);
      if (!v) continue;
      addItem(
        {
          productId: product.id,
          variantId: item.variantId,
          productName: product.name,
          optionName: v.optionName,
          price: product.price + v.additionalPrice,
        },
        item.quantity,
      );
    }
  }, [selectedItems, variantMap, addItem, product]);

  const handleAddToCart = useCallback(() => {
    addSelectedItemsToCart();
    setShowToast(true);
    setSelectedItems([]);
  }, [addSelectedItemsToCart]);

  const handleBuyNow = useCallback(() => {
    addSelectedItemsToCart();
    const keys = selectedItems.map((i) => `${product.id}:${i.variantId}`).join(',');
    router.push(`/checkout?items=${encodeURIComponent(keys)}`);
  }, [addSelectedItemsToCart, selectedItems, product.id, router]);

  const clearToast = useCallback(() => setShowToast(false), []);
  const handleDropdownClose = useCallback(() => setDropdownOpen(false), []);
  const handleDropdownToggle = useCallback(() => setDropdownOpen((o) => !o), []);

  return {
    selectedItems,
    dropdownOpen,
    variantMap,
    totalQuantity,
    totalPrice,
    canAdd,
    showToast,
    handleSelect,
    handleRemove,
    handleQuantity,
    handleDropdownToggle,
    handleDropdownClose,
    handleAddToCart,
    handleBuyNow,
    clearToast,
  };
}
