import { updateShippingStatus } from '../api/shipping-api';
import { shippingKeys } from './query-keys';
import { useInvalidatingMutation } from '@/shared/hooks';
import type { UpdateShippingStatusRequest } from '@repo/types';

export function useUpdateShippingStatus() {
  return useInvalidatingMutation({
    mutationFn: ({ shippingId, data }: { shippingId: string; data: UpdateShippingStatusRequest }) =>
      updateShippingStatus(shippingId, data),
    invalidate: [shippingKeys.all],
    errorMessage: '배송 상태 변경에 실패했습니다.',
  });
}
