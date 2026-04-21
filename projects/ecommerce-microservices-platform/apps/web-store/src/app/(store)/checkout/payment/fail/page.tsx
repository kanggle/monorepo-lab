'use client';

import { Suspense } from 'react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';

function PaymentFailContent() {
  const searchParams = useSearchParams();
  const code = searchParams.get('code');
  const message = searchParams.get('message');
  const orderId = searchParams.get('orderId');

  return (
    <div className="container" style={{ paddingTop: 'var(--space-16)', paddingBottom: 'var(--space-16)', maxWidth: '500px', textAlign: 'center' }}>
      <h1 style={{ fontSize: 'var(--font-size-2xl)', fontWeight: 'var(--font-weight-bold)', marginBottom: 'var(--space-4)' }}>
        결제 실패
      </h1>
      {code && (
        <p style={{ color: 'var(--color-text-secondary)', fontSize: 'var(--font-size-sm)', marginBottom: 'var(--space-2)' }}>
          에러 코드: {code}
        </p>
      )}
      {message && (
        <p role="alert" style={{ color: 'var(--color-error)', marginBottom: 'var(--space-6)' }}>
          {message}
        </p>
      )}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
        {orderId && (
          <Link
            href={`/checkout/payment?orderId=${orderId}`}
            className="btn btn-primary btn-lg"
            style={{ width: '100%', textAlign: 'center' }}
          >
            다시 시도
          </Link>
        )}
        <Link
          href="/cart"
          className="btn btn-lg"
          style={{ width: '100%', textAlign: 'center' }}
        >
          장바구니로 돌아가기
        </Link>
      </div>
    </div>
  );
}

export default function PaymentFailPage() {
  return (
    <Suspense fallback={null}>
      <PaymentFailContent />
    </Suspense>
  );
}
