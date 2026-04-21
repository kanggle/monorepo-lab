import { useMemo, useRef } from 'react';
import { useSearchParams } from 'next/navigation';
import type { CheckoutCartItem } from './types';

interface CartDeps {
  items: CheckoutCartItem[];
  removeItem: (productId: string, variantId: string) => void;
}

export function useCheckoutItems({ items, removeItem }: CartDeps) {
  const searchParams = useSearchParams();
  const completedRef = useRef(false);
  const snapshotRef = useRef<CheckoutCartItem[] | null>(null);

  const selectedKeys = useMemo(() => {
    const raw = searchParams.get('items');
    if (!raw) return null;
    return new Set(raw.split(','));
  }, [searchParams]);

  const checkoutItems = useMemo(() => {
    if (snapshotRef.current) return snapshotRef.current;
    if (!selectedKeys) return items;
    return items.filter((i) => selectedKeys.has(`${i.productId}:${i.variantId}`));
  }, [items, selectedKeys]);

  const totalAmount = checkoutItems.reduce((sum, i) => sum + i.price * i.quantity, 0);

  const completeOrder = () => {
    completedRef.current = true;
    snapshotRef.current = checkoutItems;
    checkoutItems.forEach((item) => removeItem(item.productId, item.variantId));
  };

  const isEmpty = !completedRef.current && checkoutItems.length === 0;

  return { checkoutItems, totalAmount, completeOrder, isEmpty };
}
