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
  discountAmount?: number;
  onOrderComplete: () => void;
}
