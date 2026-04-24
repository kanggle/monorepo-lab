import { useQuery, keepPreviousData } from '@tanstack/react-query';
import { getMyCoupons } from '../api/coupon-api';
import { couponKeys } from './query-keys';
import type { CouponStatus } from '@repo/types';

export function useCoupons(page: number, size: number, status?: CouponStatus) {
  return useQuery({
    queryKey: couponKeys.list({ page, size, status }),
    queryFn: () => getMyCoupons({ page, size, status }),
    placeholderData: keepPreviousData,
  });
}
