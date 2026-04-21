'use client';

import { PageLayout } from '@/shared/ui';
import { ErrorMessage } from '@repo/ui';
import { usePromotion } from '../hooks/use-promotion';
import { PromotionForm } from './PromotionForm';

interface Props {
  promotionId: string;
}

export function EditPromotion({ promotionId }: Props) {
  const { data: promotion, isLoading, isError, refetch } = usePromotion(promotionId);

  if (isLoading || !promotion) {
    return <PageLayout.Skeleton />;
  }

  if (isError) {
    return <ErrorMessage message="프로모션 정보를 불러오는데 실패했습니다." onRetry={() => refetch()} />;
  }

  return (
    <PageLayout title={`${promotion.name} 수정`}>
      <PromotionForm promotion={promotion} />
    </PageLayout>
  );
}
