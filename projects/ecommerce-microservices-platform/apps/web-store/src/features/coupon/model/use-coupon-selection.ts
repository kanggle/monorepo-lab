'use client';

import { useState } from 'react';
import type { ApplyCouponResponse } from '@repo/types';
import { useCoupons } from './use-coupons';
import { calculateDiscount, isCouponExpired } from '../lib/calculate-discount';

const COUPON_PAGE_SIZE = 100;

interface UseCouponSelectionOptions {
  orderAmount: number;
  onCouponApplied: (result: ApplyCouponResponse | null) => void;
}

export interface UseCouponSelectionResult {
  isOpen: boolean;
  selectedCouponId: string | null;
  expiredMessage: string | null;
  coupons: ReturnType<typeof useCoupons>['data'] extends { content: infer C } ? C : never[];
  isLoading: boolean;
  isError: boolean;
  refetch: ReturnType<typeof useCoupons>['refetch'];
  selectedCoupon: ReturnType<typeof useCoupons>['data'] extends { content: Array<infer C> } ? C | undefined : undefined;
  handleSelect: (couponId: string) => void;
  handleRemoveCoupon: () => void;
  toggleOpen: () => void;
}

export function useCouponSelection({
  orderAmount,
  onCouponApplied,
}: UseCouponSelectionOptions) {
  const [isOpen, setIsOpen] = useState(false);
  const [selectedCouponId, setSelectedCouponId] = useState<string | null>(null);
  const [expiredMessage, setExpiredMessage] = useState<string | null>(null);

  const { data, isLoading, isError, refetch } = useCoupons(0, COUPON_PAGE_SIZE, 'ISSUED');
  const coupons = data?.content ?? [];

  function handleSelect(couponId: string) {
    setExpiredMessage(null);

    if (selectedCouponId === couponId) {
      setSelectedCouponId(null);
      onCouponApplied(null);
      return;
    }

    const coupon = coupons.find((c) => c.couponId === couponId);
    if (!coupon) return;

    if (isCouponExpired(coupon)) {
      setSelectedCouponId(null);
      onCouponApplied(null);
      setExpiredMessage('쿠폰이 만료되었습니다');
      return;
    }

    const result = calculateDiscount(coupon, orderAmount);
    setSelectedCouponId(couponId);
    onCouponApplied(result);
  }

  function handleRemoveCoupon() {
    setSelectedCouponId(null);
    setExpiredMessage(null);
    onCouponApplied(null);
  }

  function toggleOpen() {
    setIsOpen((prev) => !prev);
  }

  const selectedCoupon = coupons.find((c) => c.couponId === selectedCouponId);

  return {
    isOpen,
    selectedCouponId,
    expiredMessage,
    coupons,
    isLoading,
    isError,
    refetch,
    selectedCoupon,
    handleSelect,
    handleRemoveCoupon,
    toggleOpen,
  };
}
