'use client';

import { Suspense } from 'react';
import { PageLayout } from '@/shared/ui';
import { LoadingSpinner } from '@repo/ui';
import { ShippingList } from '@/features/shipping-management';

export default function ShippingsPage() {
  return (
    <PageLayout title="배송 관리">
      <Suspense fallback={<LoadingSpinner />}>
        <ShippingList />
      </Suspense>
    </PageLayout>
  );
}
