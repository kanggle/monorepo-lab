import { useQuery } from '@tanstack/react-query';
import { getPromotion } from '../api/promotion-api';
import { promotionKeys } from './query-keys';

export function usePromotion(promotionId: string) {
  return useQuery({
    queryKey: promotionKeys.detail(promotionId),
    queryFn: () => getPromotion(promotionId),
    enabled: !!promotionId,
  });
}
