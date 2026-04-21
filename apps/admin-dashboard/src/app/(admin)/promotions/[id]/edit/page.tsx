'use client';

import { use } from 'react';
import { EditPromotion } from '@/features/promotion-management';

interface Props {
  params: Promise<{ id: string }>;
}

export default function EditPromotionPage({ params }: Props) {
  const { id } = use(params);
  return <EditPromotion promotionId={id} />;
}
