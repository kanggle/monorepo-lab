'use client';

import { use } from 'react';
import { PromotionDetail } from '@/features/promotion-management';

interface Props {
  params: Promise<{ id: string }>;
}

export default function PromotionDetailPage({ params }: Props) {
  const { id } = use(params);
  return <PromotionDetail promotionId={id} />;
}
