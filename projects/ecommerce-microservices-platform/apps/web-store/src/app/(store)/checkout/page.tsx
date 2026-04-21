'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import type { ApplyCouponResponse } from '@repo/types';
import { useRequireAuth } from '@/features/auth';
import { useCart } from '@/features/cart';
import { CheckoutForm, useCheckoutItems } from '@/features/checkout';
import { CouponSelector } from '@/features/coupon';
import { NarrowContainer } from '@/shared/ui';

export default function CheckoutPage() {
  const router = useRouter();
  const { isReady } = useRequireAuth();
  const { items, removeItem } = useCart();
  const { checkoutItems, totalAmount, completeOrder, isEmpty } = useCheckoutItems({ items, removeItem });
  const [couponResult, setCouponResult] = useState<ApplyCouponResponse | null>(null);

  if (!isReady) return null;
  if (isEmpty) {
    router.replace('/cart');
    return null;
  }

  const discountAmount = couponResult?.discountAmount ?? 0;

  return (
    <NarrowContainer>
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
