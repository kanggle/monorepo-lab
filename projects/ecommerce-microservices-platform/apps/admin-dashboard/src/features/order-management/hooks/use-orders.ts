import { useQuery } from '@tanstack/react-query';
import { getOrders } from '../api/order-api';
import { useListParams } from '@/shared/hooks';
import { toValidStatus } from '@/shared/lib/to-valid-status';
import { VALID_ORDER_STATUSES } from '@/shared/lib/status-options';
import { orderKeys } from './query-keys';

export function useOrders() {
  const { page, getParam, setFilter, buildPagination } = useListParams();

  const status = toValidStatus(getParam('status'), VALID_ORDER_STATUSES);

  const query = useQuery({
    queryKey: orderKeys.list({ page, status }),
    queryFn: () => getOrders({ page, ...(status && { status }) }),
  });

  return {
    ...query,
    pagination: buildPagination(query.data),
    filters: { status, setFilter },
  };
}
