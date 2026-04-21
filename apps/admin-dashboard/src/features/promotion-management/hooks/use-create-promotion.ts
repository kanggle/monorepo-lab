import { createPromotion } from '../api/promotion-api';
import { promotionKeys } from './query-keys';
import { useInvalidatingMutation } from '@/shared/hooks';

export function useCreatePromotion() {
  return useInvalidatingMutation({
    mutationFn: createPromotion,
    invalidate: [promotionKeys.all],
    errorMessage: '프로모션 생성에 실패했습니다.',
  });
}
