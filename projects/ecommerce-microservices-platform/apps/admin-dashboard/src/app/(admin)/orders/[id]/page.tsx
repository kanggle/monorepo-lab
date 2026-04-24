'use client';

import { use } from 'react';
import { OrderDetail } from '@/features/order-management';

interface Props {
  params: Promise<{ id: string }>;
}

export default function OrderDetailPage({ params }: Props) {
  const { id } = use(params);
  return <OrderDetail orderId={id} />;
}
