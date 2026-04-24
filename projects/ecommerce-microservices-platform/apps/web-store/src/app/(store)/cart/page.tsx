'use client';

import { CartSummary } from '@/features/cart';
import { useRequireAuth } from '@/features/auth';
import { NarrowContainer } from '@/shared/ui';

export default function CartPage() {
  const { isReady } = useRequireAuth();

  if (!isReady) return null;

  return (
    <NarrowContainer>
      <CartSummary />
    </NarrowContainer>
  );
}
