export interface CheckoutCartItem {
  productId: string;
  variantId: string;
  productName: string;
  optionName: string;
  price: number;
  quantity: number;
}

export interface CheckoutFormProps {
  items: CheckoutCartItem[];
  totalAmount: number;
  /** 화면에 보여 줄 할인 미리보기. 결제 금액에는 쓰지 않는다 — 금액은 주문 응답이 정한다. */
  discountAmount?: number;
  /** 고른 쿠폰 (TASK-INT-026). 주문 요청에 실어 서버가 할인을 확정하게 한다. */
  couponId?: string | null;
  onOrderComplete: () => void;
}
