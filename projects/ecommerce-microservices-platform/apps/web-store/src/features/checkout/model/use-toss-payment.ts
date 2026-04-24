'use client';

import { useEffect, useRef, useState } from 'react';
import { loadTossPayments } from '@tosspayments/tosspayments-sdk';

interface UseTossPaymentReturn {
  isReady: boolean;
  error: string | null;
  requestPayment: (params: {
    orderId: string;
    amount: number;
    orderName: string;
  }) => Promise<void>;
}

const TOSS_CLIENT_KEY = process.env.NEXT_PUBLIC_TOSS_CLIENT_KEY ?? '';

export function useTossPayment(): UseTossPaymentReturn {
  const [isReady, setIsReady] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const paymentRef = useRef<any>(null);

  useEffect(() => {
    let cancelled = false;

    async function init() {
      try {
        if (!TOSS_CLIENT_KEY) {
          throw new Error('NEXT_PUBLIC_TOSS_CLIENT_KEY가 설정되지 않았습니다.');
        }
        const toss = await loadTossPayments(TOSS_CLIENT_KEY);
        if (cancelled) return;
        paymentRef.current = toss.payment({ customerKey: 'ANONYMOUS' });
        setIsReady(true);
      } catch (e) {
        if (!cancelled) {
          const msg = e instanceof Error ? e.message : '알 수 없는 오류';
          setError(`결제 모듈을 불러오는데 실패했습니다: ${msg}`);
        }
      }
    }

    init();
    return () => { cancelled = true; };
  }, []);

  async function requestPayment(params: {
    orderId: string;
    amount: number;
    orderName: string;
  }) {
    if (!paymentRef.current) {
      throw new Error('결제 모듈이 준비되지 않았습니다.');
    }

    const origin = window.location.origin;
    await paymentRef.current.requestPayment({
      method: 'CARD',
      amount: { currency: 'KRW', value: params.amount },
      orderId: params.orderId,
      orderName: params.orderName,
      successUrl: `${origin}/checkout/payment/success`,
      failUrl: `${origin}/checkout/payment/fail`,
    });
  }

  return { isReady, error, requestPayment };
}
