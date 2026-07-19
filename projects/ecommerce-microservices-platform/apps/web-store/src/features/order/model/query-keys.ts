import { createListQueryKeys } from '@/shared/lib/query-keys';

const orderBase = createListQueryKeys('orders');

export const orderKeys = {
  ...orderBase,
  details: () => [...orderBase.all, 'detail'] as const,
  detail: (id: string) => [...orderKeys.details(), id] as const,
};

export const paymentKeys = {
  all: ['payments'] as const,
  detail: (orderId: string) => [...paymentKeys.all, orderId] as const,
};

export const shippingKeys = {
  all: ['shippings'] as const,
  byOrder: (orderId: string) => [...shippingKeys.all, 'order', orderId] as const,
};
