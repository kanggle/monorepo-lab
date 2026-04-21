'use client';

import { useRequireAuth } from '@/features/auth';
import { OrderHistory } from '@/features/order';
import { NarrowContainer } from '@/shared/ui';

export default function OrdersPage() {
  const { isReady } = useRequireAuth();

  if (!isReady) return null;

  return (
    <NarrowContainer>
      <OrderHistory />
    </NarrowContainer>
  );
}
