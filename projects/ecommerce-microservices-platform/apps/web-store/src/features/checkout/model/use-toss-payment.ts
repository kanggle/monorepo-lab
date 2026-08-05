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

/**
 * The demo branch lives HERE, in the hook, rather than in the two components that call it
 * (TASK-BE-572 AC-4). `CheckoutForm` and `PaymentWidget` both consume this hook and both break in
 * the demo — the form throws "결제 모듈이 준비되지 않았습니다" AFTER the order was already placed,
 * and the widget renders the "결제 모듈을 불러오는데 실패했습니다" banner. Branching once here
 * fixes both without either component learning that a demo mode exists; branching in the callers
 * would have been two copies of the same condition, and one of them would eventually be forgotten.
 *
 * The demo path deliberately reuses the REAL confirm flow: it navigates to the same
 * `/checkout/payment/success` URL, with the same query parameters, that Toss itself redirects to.
 * Everything after that point — `usePaymentConfirmation`, the confirm API call, the cart clear,
 * the redirect to `/checkout/complete` — is byte-identical to production. A demo that shortcut
 * straight to the completion page would be demonstrating a code path nobody ships.
 */
async function fetchDemoPaymentFlag(signal: AbortSignal): Promise<boolean> {
  const res = await fetch('/api/store-config', { signal, cache: 'no-store' });
  if (!res.ok) return false;
  const body = (await res.json()) as { demoPayment?: boolean };
  return body.demoPayment === true;
}

export function useTossPayment(): UseTossPaymentReturn {
  const [isReady, setIsReady] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const paymentRef = useRef<any>(null);
  const demoRef = useRef(false);

  useEffect(() => {
    const controller = new AbortController();
    let cancelled = false;

    async function init() {
      // Ask BEFORE touching the SDK. Loading Toss with the demo's dummy key is what produces the
      // failure banner, so the order of these two steps is the fix — not an optimisation.
      let demo = false;
      try {
        demo = await fetchDemoPaymentFlag(controller.signal);
      } catch {
        // Unreachable config ⇒ behave exactly as before this change: try the real SDK. Defaulting
        // the other way would let a transient fetch failure silently switch a real storefront into
        // "every payment succeeds".
        demo = false;
      }
      if (cancelled) return;

      if (demo) {
        demoRef.current = true;
        setIsReady(true);
        return;
      }

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
    return () => {
      cancelled = true;
      controller.abort();
    };
  }, []);

  async function requestPayment(params: {
    orderId: string;
    amount: number;
    orderName: string;
  }) {
    const origin = window.location.origin;

    if (demoRef.current) {
      // The same URL Toss would redirect to. `paymentKey` is minted here because there is no PG
      // to mint one; payment-service's demo gateway echoes it back as the vendor reference.
      const query = new URLSearchParams({
        paymentKey: `demo_${params.orderId}`,
        orderId: params.orderId,
        amount: String(params.amount),
      });
      window.location.assign(`${origin}/checkout/payment/success?${query.toString()}`);
      return;
    }

    if (!paymentRef.current) {
      throw new Error('결제 모듈이 준비되지 않았습니다.');
    }

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
