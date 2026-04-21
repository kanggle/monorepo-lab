'use client';

import { useParams } from 'next/navigation';
import { useRequireAuth } from '@/features/auth';
import { OrderDetailView } from '@/features/order';
import { NarrowContainer } from '@/shared/ui';

export default function OrderDetailPage() {
  const params = useParams<{ id: string }>();
  const { isReady } = useRequireAuth();

  if (!isReady) return null;

  return (
    <NarrowContainer>
      <OrderDetailView orderId={params.id} />
    </NarrowContainer>
  );
}
