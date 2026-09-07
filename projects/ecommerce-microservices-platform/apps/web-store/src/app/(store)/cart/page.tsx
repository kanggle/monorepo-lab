'use client';

import { CartSummary } from '@/features/cart';
import { useRequireAuth } from '@/features/auth';
import { NarrowContainer } from '@/shared/ui';
import { DemoHeartbeat } from '@/features/demo-heartbeat';

export default function CartPage() {
  const { isReady } = useRequireAuth();

  if (!isReady) return null;

  return (
    <NarrowContainer>
      {/* 🔴 로그인 영역에서만 하트비트를 보낸다 — 공개 열람은 EC2 를 켜 두면 안 된다
          (근거는 `DemoHeartbeat` 머리 주석). */}
      <DemoHeartbeat />
      <CartSummary />
    </NarrowContainer>
  );
}
