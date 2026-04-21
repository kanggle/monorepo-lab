import { useQuery } from '@tanstack/react-query';
import { getProducts } from '../api/product-api';
import { useListParams } from '@/shared/hooks';
import { toValidStatus } from '@/shared/lib/to-valid-status';
import { VALID_PRODUCT_STATUSES } from '@/shared/lib/status-options';
import { productKeys } from './query-keys';

export function useProducts() {
  const { page, getParam, setFilter, buildPagination } = useListParams();

  const status = toValidStatus(getParam('status'), VALID_PRODUCT_STATUSES);
  const name = getParam('name') || undefined;

  const query = useQuery({
    queryKey: productKeys.list({ page, status, name }),
    queryFn: () => getProducts({ page, status, name }),
  });

  return {
    ...query,
    pagination: buildPagination(query.data),
    filters: { status, name, setFilter },
  };
}
