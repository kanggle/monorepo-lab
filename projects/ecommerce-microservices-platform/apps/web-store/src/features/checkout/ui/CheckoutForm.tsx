'use client';

import { useState } from 'react';
import { isApiError, ERROR_MESSAGES } from '@repo/types/guards';
import type { CheckoutFormProps } from '../model/types';
import { placeOrder } from '@/entities/order';
import { useTossPayment } from '../model/use-toss-payment';
import { useAddresses } from '@/entities/user';
import { isValidPhone } from '@/shared/lib/validate-phone';
import { useShippingAddressState } from '../model/use-shipping-address-state';
import { OrderItemsSection } from './OrderItemsSection';
import { AddressSection } from './AddressSection';
import { PriceDisplay } from '@/shared/ui';

export function CheckoutForm({ items, totalAmount, discountAmount = 0, onOrderComplete }: CheckoutFormProps) {
  const finalAmount = totalAmount - discountAmount;
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
      const orderItems = items.map((item) => ({
        productId: item.productId, variantId: item.variantId,
        productName: item.productName, optionName: item.optionName,
        quantity: item.quantity, unitPrice: item.price,
      }));
      const result = await placeOrder({ items: orderItems, shippingAddress: address });
      const orderName = items[0].productName + (items.length > 1 ? ` 외 ${items.length - 1}건` : '');
      onOrderComplete();
      await requestPayment({ orderId: result.orderId, amount: finalAmount, orderName });
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
        {isSubmitting ? '주문 처리 중...' : <><PriceDisplay amount={finalAmount} unitStyle={{ fontSize: 'var(--font-size-sm)', fontWeight: 'var(--font-weight-normal)', margin: '0 var(--space-2) 0 2px' }} />결제하기</>}
      </button>
    </form>
  );
}
