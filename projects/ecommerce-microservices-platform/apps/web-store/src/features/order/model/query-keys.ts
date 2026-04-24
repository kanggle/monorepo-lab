export const orderKeys = {
  all: ['orders'] as const,
  lists: () => [...orderKeys.all, 'list'] as const,
  list: (params: Record<string, unknown>) => [...orderKeys.lists(), params] as const,
  details: () => [...orderKeys.all, 'detail'] as const,
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
