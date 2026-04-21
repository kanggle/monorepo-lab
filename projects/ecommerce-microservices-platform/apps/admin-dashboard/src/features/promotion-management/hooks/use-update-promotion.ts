import { updatePromotion } from '../api/promotion-api';
import { promotionKeys } from './query-keys';
import { useInvalidatingMutation } from '@/shared/hooks';
import type { UpdatePromotionRequest } from '@repo/types';

export function useUpdatePromotion() {
  return useInvalidatingMutation({
    mutationFn: ({ promotionId, data }: { promotionId: string; data: UpdatePromotionRequest }) =>
      updatePromotion(promotionId, data),
    invalidate: (variables) => [promotionKeys.all, promotionKeys.detail(variables.promotionId)],
    errorMessage: '프로모션 수정에 실패했습니다.',
  });
}
