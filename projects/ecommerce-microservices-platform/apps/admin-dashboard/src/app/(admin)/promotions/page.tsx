'use client';

import { Suspense } from 'react';
import { PageLayout } from '@/shared/ui';
import { LoadingSpinner } from '@repo/ui';
import { PromotionList } from '@/features/promotion-management';

export default function PromotionsPage() {
  return (
    <PageLayout title="프로모션 관리" actions={[{ label: '프로모션 등록', href: '/promotions/new' }]}>
      <Suspense fallback={<LoadingSpinner />}>
        <PromotionList />
      </Suspense>
    </PageLayout>
  );
}
