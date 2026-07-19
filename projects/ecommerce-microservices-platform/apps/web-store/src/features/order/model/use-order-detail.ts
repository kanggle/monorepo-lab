import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import type { OrderDetail, PaymentResponse } from '@repo/types';
import { isApiError } from '@repo/types/guards';
import { mapQueryError } from '@/shared/lib/map-query-error';
import { getOrder, cancelOrder } from '@/entities/order';
import { getPayment } from '@/entities/payment';
import { orderKeys, paymentKeys } from './query-keys';

export const CANCELLABLE_STATUSES = new Set(['PENDING', 'CONFIRMED']);

export interface UseOrderDetailReturn {
  order: OrderDetail | null;
  payment: PaymentResponse | null;
  paymentError: string;
  isLoading: boolean;
  error: string;
  isCancelling: boolean;
  handleCancel: () => Promise<void>;
  retryLoad: () => void;
}

export function useOrderDetail(orderId: string): UseOrderDetailReturn {
  const queryClient = useQueryClient();
  const [cancelError, setCancelError] = useState('');
  const [isCancelling, setIsCancelling] = useState(false);

  const orderQuery = useQuery({
    queryKey: orderKeys.detail(orderId),
    queryFn: () => getOrder(orderId),
    enabled: !!orderId,
  });

  const paymentQuery = useQuery({
    queryKey: paymentKeys.detail(orderId),
    queryFn: () => getPayment(orderId),
    enabled: !!orderId && !!orderQuery.data,
    retry: false,
  });

  const orderError = mapQueryError(orderQuery.error, {
    notFoundCode: 'ORDER_NOT_FOUND',
    notFoundMessage: '주문을 찾을 수 없습니다.',
    fallbackMessage: '주문 정보를 불러오는데 실패했습니다.',
  });

  const paymentError = mapQueryError(paymentQuery.error, {
    notFoundCode: 'PAYMENT_NOT_FOUND',
    notFoundMessage: '',
    fallbackMessage: '결제 정보를 불러오는데 실패했습니다.',
  });

  async function handleCancel() {
    if (!orderQuery.data || isCancelling) return;
    setCancelError('');
    setIsCancelling(true);
    try {
      await cancelOrder(orderQuery.data.orderId);
      queryClient.setQueryData(orderKeys.detail(orderId), {
        ...orderQuery.data,
        status: 'CANCELLED',
      });
      queryClient.invalidateQueries({ queryKey: orderKeys.lists() });
    } catch (err) {
      if (isApiError(err)) {
        setCancelError(err.message ?? '주문 취소에 실패했습니다.');
      } else {
        setCancelError('주문 취소에 실패했습니다.');
      }
    } finally {
      setIsCancelling(false);
    }
  }

  function retryLoad() {
    orderQuery.refetch();
  }

  return {
    order: orderQuery.data ?? null,
    payment: paymentQuery.data ?? null,
    paymentError,
    isLoading: orderQuery.isLoading,
    error: cancelError || orderError,
    isCancelling,
    handleCancel,
    retryLoad,
  };
}
