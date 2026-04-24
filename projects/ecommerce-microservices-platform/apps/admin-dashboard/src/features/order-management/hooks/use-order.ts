import { useQuery } from '@tanstack/react-query';
import { getOrder } from '../api/order-api';
import { orderKeys } from './query-keys';

export function useOrder(orderId: string) {
  return useQuery({
    queryKey: orderKeys.detail(orderId),
    queryFn: () => getOrder(orderId),
    enabled: !!orderId,
  });
}
