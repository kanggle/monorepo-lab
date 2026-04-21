import { changeOrderStatus } from '../api/order-api';
import { orderKeys } from './query-keys';
import { useInvalidatingMutation } from '@/shared/hooks';
import type { OrderStatus } from '@repo/types';

export function useChangeOrderStatus(orderId: string) {
  return useInvalidatingMutation({
    mutationFn: (status: OrderStatus) => changeOrderStatus(orderId, status),
    invalidate: [orderKeys.detail(orderId), orderKeys.all],
  });
}
