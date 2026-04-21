import { useEffect, useRef } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { useMutation } from '@tanstack/react-query';
import { confirmPayment } from '@/entities/payment';

export type PaymentConfirmationStatus = 'invalid' | 'pending' | 'error' | 'redirecting';

export function usePaymentConfirmation() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const calledRef = useRef(false);
  const confirmMutation = useMutation({
    mutationFn: (params: { paymentKey: string; orderId: string; amount: number }) =>
      confirmPayment(params),
  });

  const paymentKey = searchParams.get('paymentKey');
  const orderId = searchParams.get('orderId');
  const amount = Number(searchParams.get('amount'));

  const hasValidParams = !!paymentKey && !!orderId && !isNaN(amount) && amount > 0;

  useEffect(() => {
    if (calledRef.current || !hasValidParams) return;

    calledRef.current = true;

    confirmMutation.mutate(
      { paymentKey: paymentKey!, orderId: orderId!, amount },
      {
        onSuccess: () => {
          router.replace(`/checkout/complete?orderId=${orderId}`);
        },
      },
    );
    // calledRef prevents re-execution; confirmMutation identity changes each render
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hasValidParams, paymentKey, orderId, amount, router]);

  const status: PaymentConfirmationStatus = !hasValidParams
    ? 'invalid'
    : confirmMutation.isError
      ? 'error'
      : confirmMutation.isPending || confirmMutation.isIdle
        ? 'pending'
        : 'redirecting';

  const errorMessage = confirmMutation.isError
    ? (confirmMutation.error?.message ?? '결제 승인에 실패했습니다.')
    : null;

  const goToCart = () => router.push('/cart');
  const retry = () => {
    if (orderId) {
      router.push(`/checkout/payment?orderId=${orderId}&amount=${amount}&orderName=재시도`);
    } else {
      router.push('/cart');
    }
  };

  return { status, errorMessage, goToCart, retry };
}
