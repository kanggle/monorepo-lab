import type { ProductStatus, OrderStatus, UserStatus, PromotionStatus, ShippingStatus } from '@repo/types';

export interface StatusOption<T extends string = string> {
  label: string;
  value: T;
}

export const PRODUCT_STATUS_OPTIONS: StatusOption<ProductStatus>[] = [
  { label: '판매중', value: 'ON_SALE' },
  { label: '품절', value: 'SOLD_OUT' },
  { label: '숨김', value: 'HIDDEN' },
];

export const ORDER_STATUS_OPTIONS: StatusOption<OrderStatus>[] = [
  { label: '대기', value: 'PENDING' },
  { label: '확인', value: 'CONFIRMED' },
  { label: '배송중', value: 'SHIPPED' },
  { label: '배송완료', value: 'DELIVERED' },
  { label: '취소', value: 'CANCELLED' },
];

export const USER_STATUS_OPTIONS: StatusOption<UserStatus>[] = [
  { label: '활성', value: 'ACTIVE' },
  { label: '정지', value: 'SUSPENDED' },
  { label: '탈퇴', value: 'WITHDRAWN' },
];

export const PROMOTION_STATUS_OPTIONS: StatusOption<PromotionStatus>[] = [
  { label: '예정', value: 'SCHEDULED' },
  { label: '진행중', value: 'ACTIVE' },
  { label: '종료', value: 'ENDED' },
];

export const SHIPPING_STATUS_OPTIONS: StatusOption<ShippingStatus>[] = [
  { label: '준비중', value: 'PREPARING' },
  { label: '발송완료', value: 'SHIPPED' },
  { label: '운송중', value: 'IN_TRANSIT' },
  { label: '배송완료', value: 'DELIVERED' },
];

export const VALID_PRODUCT_STATUSES: readonly ProductStatus[] = PRODUCT_STATUS_OPTIONS.map((o) => o.value);
export const VALID_ORDER_STATUSES: readonly OrderStatus[] = ORDER_STATUS_OPTIONS.map((o) => o.value);
export const VALID_USER_STATUSES: readonly UserStatus[] = USER_STATUS_OPTIONS.map((o) => o.value);
export const VALID_PROMOTION_STATUSES: readonly PromotionStatus[] = PROMOTION_STATUS_OPTIONS.map((o) => o.value);
export const VALID_SHIPPING_STATUSES: readonly ShippingStatus[] = SHIPPING_STATUS_OPTIONS.map((o) => o.value);
