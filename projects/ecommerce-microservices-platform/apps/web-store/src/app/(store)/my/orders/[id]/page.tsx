'use client';

import { useParams } from 'next/navigation';
import { OrderDetailView } from '@/features/order';

export default function MyOrderDetailPage() {
  const params = useParams<{ id: string }>();

  return <OrderDetailView orderId={params.id} />;
}
