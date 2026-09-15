'use client';

import { useState } from 'react';
import { isApiError, ERROR_MESSAGES } from '@repo/types/guards';
import type { CheckoutFormProps } from '../model/types';
import { placeOrder } from '@/entities/order';
import { useTossPayment } from '../model/use-toss-payment';
import { getOrCreateIdempotencyKey } from '../model/checkout-idempotency';
import { orderLineVerdictMessage, verifyOrderLines } from '../model/verify-order-lines';
import { useAddresses } from '@/entities/user';
import { isValidPhone } from '@/shared/lib/validate-phone';
import { useShippingAddressState } from '../model/use-shipping-address-state';
import { OrderItemsSection } from './OrderItemsSection';
import { AddressSection } from './AddressSection';
import { PriceDisplay } from '@/shared/ui';

export function CheckoutForm({
  items,
  totalAmount,
  discountAmount = 0,
  couponId = null,
  onOrderComplete,
}: CheckoutFormProps) {
  // 화면에 보여 주는 미리보기일 뿐이다. 결제 금액은 주문 응답의 totalPrice 가 정한다 (TASK-INT-026).
  const previewAmount = totalAmount - discountAmount;
  const { requestPayment } = useTossPayment();
  const [error, setError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const { data: addressData, isLoading: addressLoading } = useAddresses();
  const savedAddresses = addressData?.addresses ?? [];

  const {
    selectedAddressId,
    address,
    handleAddressSelect,
    handleAddressSearchSelect,
    updateField,
  } = useShippingAddressState(savedAddresses, addressData);

  const phoneValid = isValidPhone(address.phone);
  const isValid =
    address.recipient.trim().length > 0 && phoneValid &&
    address.zipCode.trim().length > 0 && address.address1.trim().length > 0;
  const isNewAddress = selectedAddressId === 'new' || selectedAddressId === '';

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!isValid || isSubmitting || items.length === 0) return;

    setError('');
    setIsSubmitting(true);

    try {
      // 🔴🔴 주문을 만들기 **전에** 라이브 백엔드로 각 줄을 다시 확인한다. 공개 카탈로그는
      //    이제 공개 저장본(표시 가격, 재고 없음)을 읽으므로, 장바구니의 가격은 «발행 시점의
      //    값» 이고 재고는 **아무도 안 물어본 상태**로 여기까지 온다. 근거와 «백엔드에
      //    동기 재검증이 없다» 는 실측은 `verify-order-lines.ts` 머리 주석에 있다.
      //    백엔드가 죽어 있으면 여기서 **거절한다** — 성공을 흉내내지 않는다.
      const verdict = await verifyOrderLines(items);
      if (!verdict.ok) {
        setError(orderLineVerdictMessage(verdict));
        setIsSubmitting(false);
        return;
      }

      const orderItems = items.map((item) => ({
        productId: item.productId, variantId: item.variantId,
        productName: item.productName, optionName: item.optionName,
        quantity: item.quantity, unitPrice: item.price,
      }));
      // Stable per-checkout idempotency key (TASK-BE-430): a retry of the same checkout
      // (payment failure / back / double-submit) reuses it so the server returns the
      // original order instead of creating a duplicate. The coupon is part of the key
      // (TASK-INT-026) — a different coupon is a differently priced order.
      const idempotencyKey = getOrCreateIdempotencyKey(items, couponId);
      const result = await placeOrder(
        { items: orderItems, shippingAddress: address, ...(couponId ? { couponId } : {}) },
        idempotencyKey,
      );
      // 🔴 TASK-INT-026 — 결제 금액의 권위는 서버다. payment-service 는 OrderPlaced.totalPrice 로
      //    PENDING 결제를 만들고, 승인 금액이 그와 다르면 AMOUNT_MISMATCH 로 거절한다. 화면이
      //    계산한 할인(previewAmount)을 보내면 쿠폰 주문이 전부 승인에서 막힌다. 금액을 모르면
      //    결제를 시작하지 않는다 — 추정한 금액으로 결제창을 열지 않는다.
      if (typeof result.totalPrice !== 'number' || result.totalPrice < 1) {
        setError('주문 금액을 확인할 수 없어 결제를 진행할 수 없습니다. 잠시 후 다시 시도해 주세요.');
        setIsSubmitting(false);
        return;
      }
      const orderName = items[0].productName + (items.length > 1 ? ` 외 ${items.length - 1}건` : '');
      // Mark the checkout placed (snapshot the lines so the page doesn't flash empty
      // during the Toss redirect). The cart is cleared on the payment-complete page,
      // NOT here — clearing before payment raced the redirect and left orphan orders.
      onOrderComplete();
      await requestPayment({ orderId: result.orderId, amount: result.totalPrice, orderName });
    } catch (err) {
      if (isApiError(err)) {
        setError(ERROR_MESSAGES[err.code] ?? err.message ?? '주문에 실패했습니다.');
      } else {
        setError('주문에 실패했습니다.');
      }
      setIsSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate>
      <h1 className="page-title">주문하기</h1>

      {error && <div role="alert" className="alert-error">{error}</div>}

      <OrderItemsSection items={items} totalAmount={totalAmount} discountAmount={discountAmount} />

      <AddressSection
        addressLoading={addressLoading}
        savedAddresses={savedAddresses}
        selectedAddressId={selectedAddressId}
        address={address}
        phoneValid={phoneValid}
        isNewAddress={isNewAddress}
        onAddressSelect={handleAddressSelect}
        onAddressSearchSelect={handleAddressSearchSelect}
        onFieldChange={updateField}
      />

      <button
        type="submit"
        disabled={!isValid || isSubmitting || items.length === 0}
        className="btn btn-accent btn-lg"
        style={{
          width: '100%',
          opacity: !isValid || isSubmitting ? 0.5 : 1,
        }}
      >
        {isSubmitting ? '주문 처리 중...' : <><PriceDisplay amount={previewAmount}unitStyle={{ fontSize: 'var(--font-size-sm)', fontWeight: 'var(--font-weight-normal)', margin: '0 var(--space-2) 0 2px' }} />결제하기</>}
      </button>
    </form>
  );
}
