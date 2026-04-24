import { deletePromotion } from '../api/promotion-api';
import { promotionKeys } from './query-keys';
import { useInvalidatingMutation } from '@/shared/hooks';

export function useDeletePromotion() {
  return useInvalidatingMutation({
    mutationFn: deletePromotion,
    invalidate: [promotionKeys.all],
    errorMessage: '프로모션 삭제에 실패했습니다.',
  });
}
