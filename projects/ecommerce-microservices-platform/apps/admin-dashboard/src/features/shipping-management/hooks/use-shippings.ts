import { useQuery } from '@tanstack/react-query';
import { getShippings } from '../api/shipping-api';
import { useListParams } from '@/shared/hooks';
import { toValidStatus } from '@/shared/lib/to-valid-status';
import { VALID_SHIPPING_STATUSES } from '@/shared/lib/status-options';
import { shippingKeys } from './query-keys';

export function useShippings() {
  const { page, getParam, setFilter, buildPagination } = useListParams();

  const status = toValidStatus(getParam('status'), VALID_SHIPPING_STATUSES);

  const query = useQuery({
    queryKey: shippingKeys.list({ page, status }),
    queryFn: () => getShippings({ page, ...(status && { status }) }),
  });

  return {
    ...query,
    pagination: buildPagination(query.data),
    filters: { status, setFilter },
  };
}
