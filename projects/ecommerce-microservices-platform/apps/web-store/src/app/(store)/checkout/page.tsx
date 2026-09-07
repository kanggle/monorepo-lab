'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import type { ApplyCouponResponse } from '@repo/types';
import { useRequireAuth } from '@/features/auth';
import { useCart } from '@/features/cart';
import { CheckoutForm, useCheckoutItems } from '@/features/checkout';
import { CouponSelector } from '@/features/coupon';
import { NarrowContainer } from '@/shared/ui';
import { DemoHeartbeat } from '@/features/demo-heartbeat';

export default function CheckoutPage() {
  const router = useRouter();
  const { isReady } = useRequireAuth();
  const { items } = useCart();
  const { checkoutItems, totalAmount, completeOrder, isEmpty } = useCheckoutItems({ items });
  const [couponResult, setCouponResult] = useState<ApplyCouponResponse | null>(null);

  if (!isReady) return null;
  if (isEmpty) {
    router.replace('/cart');
    return null;
  }

  const discountAmount = couponResult?.discountAmount ?? 0;

  return (
    <NarrowContainer>
      {/* 🔴 로그인 영역에서만 하트비트를 보낸다 — 공개 열람은 EC2 를 켜 두면 안 된다
          (근거는 `DemoHeartbeat` 머리 주석). */}
      <DemoHeartbeat />
      <CouponSelector
        orderAmount={totalAmount}
        onCouponApplied={setCouponResult}
      />
      <CheckoutForm
        items={checkoutItems}
        totalAmount={totalAmount}
        discountAmount={discountAmount}
        onOrderComplete={completeOrder}
      />
    </NarrowContainer>
  );
}
