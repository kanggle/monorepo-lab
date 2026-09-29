'use client';

import { CartSummary } from '@/features/cart';
import { useAuth } from '@/features/auth';
import { NarrowContainer } from '@/shared/ui';
import { DemoHeartbeat } from '@/features/demo-heartbeat';

/**
 * TASK-FE-102 — public: a logged-out visitor sees the guest cart here. Checkout from
 * this page still requires login (`/checkout` stays gated in middleware).
 */
export default function CartPage() {
  const { isAuthenticated } = useAuth();

  return (
    <NarrowContainer>
      {/* 🔴 로그인 영역에서만 하트비트를 보낸다 — 공개 열람은 EC2 를 켜 두면 안 된다
          (근거는 `DemoHeartbeat` 머리 주석). 이 페이지는 이제 비로그인도 열므로
          로그인일 때만 붙인다(TASK-FE-102). */}
      {isAuthenticated && <DemoHeartbeat />}
      <CartSummary />
    </NarrowContainer>
  );
}
