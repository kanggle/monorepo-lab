import { useMemo, useRef } from 'react';
import { useSearchParams } from 'next/navigation';
import type { CheckoutCartItem } from './types';

interface CartDeps {
  items: CheckoutCartItem[];
}

export function useCheckoutItems({ items }: CartDeps) {
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

  // Marks the checkout as placed and snapshots the lines so the page keeps showing
  // them (no empty flash) through the Toss redirect. The cart is NOT cleared here:
  // clearing before payment raced the redirect and left orphan orders on
  // failure/abandon (TASK-BE-430). The cart is cleared on the payment-complete page.
  const completeOrder = () => {
    completedRef.current = true;
    snapshotRef.current = checkoutItems;
  };

  const isEmpty = !completedRef.current && checkoutItems.length === 0;

  return { checkoutItems, totalAmount, completeOrder, isEmpty };
}
