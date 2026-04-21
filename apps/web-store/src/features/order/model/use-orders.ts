import { useQuery, keepPreviousData } from '@tanstack/react-query';
import { getOrders } from '@/entities/order';
import { orderKeys } from './query-keys';

export function useOrders(page: number, size: number) {
  return useQuery({
    queryKey: orderKeys.list({ page, size }),
    queryFn: () => getOrders(page, size),
    placeholderData: keepPreviousData,
  });
}
