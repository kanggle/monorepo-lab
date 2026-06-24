'use client';

import { Suspense, useEffect } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { useCart } from '@/features/cart';
import { clearCheckoutIdempotencyKey } from '@/features/checkout';

function CheckoutCompleteContent() {
  const searchParams = useSearchParams();
  const orderId = searchParams.get('orderId');
  const { clearCart } = useCart();

  // Clear the cart + the checkout idempotency key only after a confirmed payment
  // (TASK-BE-430). Doing this here — not at order placement — means a failed/abandoned
  // payment keeps the cart so the user can retry, and the retry reuses the same key
  // (no duplicate order). A completed payment starts the next purchase fresh.
  useEffect(() => {
    if (orderId) {
      clearCart();
      clearCheckoutIdempotencyKey();
    }
  }, [orderId, clearCart]);

  return (
    <div className="container" style={{ paddingTop: 'var(--space-16)', paddingBottom: 'var(--space-16)', maxWidth: '500px', textAlign: 'center' }}>
      <div style={{ fontSize: '3rem', marginBottom: 'var(--space-4)' }}>
        ✓
      </div>
      <h1 style={{ fontSize: 'var(--font-size-2xl)', fontWeight: 'var(--font-weight-bold)', marginBottom: 'var(--space-3)' }}>
        주문이 완료되었습니다
      </h1>
      <p style={{ color: 'var(--color-text-secondary)', fontSize: 'var(--font-size-sm)', marginBottom: 'var(--space-2)' }}>
        주문해 주셔서 감사합니다.
      </p>
      {orderId && (
        <p style={{ color: 'var(--color-text-muted)', fontSize: 'var(--font-size-xs)', marginBottom: 'var(--space-8)' }}>
          주문번호: {orderId}
        </p>
      )}

      <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
        {orderId && (
          <Link
            href={`/my/orders/${orderId}`}
            className="btn btn-primary btn-lg"
            style={{ width: '100%', textAlign: 'center' }}
          >
            주문 상세 보기
          </Link>
        )}
        <Link
          href="/products"
          className="btn btn-lg"
          style={{ width: '100%', textAlign: 'center' }}
        >
          쇼핑 계속하기
        </Link>
      </div>
    </div>
  );
}

export default function CheckoutCompletePage() {
  return (
    <Suspense fallback={null}>
      <CheckoutCompleteContent />
    </Suspense>
  );
}
