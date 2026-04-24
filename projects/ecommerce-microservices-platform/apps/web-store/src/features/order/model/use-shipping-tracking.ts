import { useQuery } from '@tanstack/react-query';
import type { ShippingResponse } from '@repo/types';
import { isApiError } from '@repo/types/guards';
import { getShippingByOrder } from '../api/shipping-api';
import { shippingKeys } from './query-keys';

export interface UseShippingTrackingReturn {
  shipping: ShippingResponse | null;
  isLoading: boolean;
  isNotFound: boolean;
  error: string;
}

export function useShippingTracking(orderId: string): UseShippingTrackingReturn {
  const query = useQuery({
    queryKey: shippingKeys.byOrder(orderId),
    queryFn: () => getShippingByOrder(orderId),
    enabled: !!orderId,
    retry: (failureCount, error) => {
      if (isApiError(error) && ['SHIPPING_NOT_FOUND', 'ACCESS_DENIED'].includes(error.code)) {
        return false;
      }
      return failureCount < 3;
    },
  });

  const isNotFound = query.error
    ? isApiError(query.error) && query.error.code === 'SHIPPING_NOT_FOUND'
    : false;

  const error = query.error
    ? isNotFound
      ? ''
      : isApiError(query.error) && query.error.code === 'ACCESS_DENIED'
        ? '배송 정보에 접근할 수 없습니다.'
        : '배송 정보를 불러오는데 실패했습니다.'
    : '';

  return {
    shipping: query.data ?? null,
    isLoading: query.isLoading,
    isNotFound,
    error,
  };
}
