import { refreshTracking } from '../api/shipping-api';
import { shippingKeys } from './query-keys';
import { useInvalidatingMutation } from '@/shared/hooks';

// TASK-FE-073 — operator-triggered carrier sync. Calls the best-effort admin
// `refresh-tracking` endpoint (BE-293), then invalidates the shipping list so a
// forward-advanced status (e.g. IN_TRANSIT → DELIVERED) renders immediately.
// No optimistic mutation: the carrier result is authoritative server-side.
export function useRefreshTracking() {
  return useInvalidatingMutation({
    mutationFn: ({ shippingId }: { shippingId: string }) => refreshTracking(shippingId),
    invalidate: [shippingKeys.all],
    errorMessage: '택배사 동기화에 실패했습니다.',
  });
}
