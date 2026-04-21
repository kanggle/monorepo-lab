import { issueCoupons } from '../api/promotion-api';
import { promotionKeys } from './query-keys';
import { useInvalidatingMutation } from '@/shared/hooks';
import type { IssueCouponsRequest } from '@repo/types';

export function useIssueCoupons(promotionId: string) {
  return useInvalidatingMutation({
    mutationFn: (data: IssueCouponsRequest) => issueCoupons(promotionId, data),
    invalidate: [promotionKeys.detail(promotionId), promotionKeys.all],
    errorMessage: '쿠폰 발급에 실패했습니다.',
  });
}
