'use client';

import { Suspense } from 'react';
import { PageLayout } from '@/shared/ui';
import { LoadingSpinner } from '@repo/ui';
import { OrderList } from '@/features/order-management';

export default function OrdersPage() {
  return (
    <PageLayout title="주문 관리">
      <Suspense fallback={<LoadingSpinner />}>
        <OrderList />
      </Suspense>
    </PageLayout>
  );
}
