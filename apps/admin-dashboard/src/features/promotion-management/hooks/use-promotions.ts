import { useQuery } from '@tanstack/react-query';
import { getPromotions } from '../api/promotion-api';
import { useListParams } from '@/shared/hooks';
import { toValidStatus } from '@/shared/lib/to-valid-status';
import { VALID_PROMOTION_STATUSES } from '@/shared/lib/status-options';
import { promotionKeys } from './query-keys';

export function usePromotions() {
  const { page, getParam, setFilter, buildPagination } = useListParams();

  const status = toValidStatus(getParam('status'), VALID_PROMOTION_STATUSES);

  const query = useQuery({
    queryKey: promotionKeys.list({ page, status }),
    queryFn: () => getPromotions({ page, status }),
  });

  return {
    ...query,
    pagination: buildPagination(query.data),
    filters: { status, setFilter },
  };
}
