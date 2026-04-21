'use client';

import { Suspense } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';

function CheckoutCompleteContent() {
  const searchParams = useSearchParams();
  const orderId = searchParams.get('orderId');

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
