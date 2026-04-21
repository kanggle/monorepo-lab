'use client';

import { PageLayout } from '@/shared/ui';
import { PromotionForm } from '@/features/promotion-management';

export default function NewPromotionPage() {
  return (
    <PageLayout title="프로모션 등록" actions={[{ label: '← 프로모션 관리', href: '/promotions', variant: 'secondary' as const }]}>
      <PromotionForm />
    </PageLayout>
  );
}
