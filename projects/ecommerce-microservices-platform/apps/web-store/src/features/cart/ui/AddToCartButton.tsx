'use client';

import { useCallback, useState } from 'react';
import { useCart } from '../model/cart-context';
import { Toast } from '@/shared/ui';

interface AddToCartButtonProps {
  productId: string;
  variantId: string;
  productName: string;
  optionName: string;
  price: number;
  quantity?: number;
  disabled?: boolean;
}

export function AddToCartButton({
  productId,
  variantId,
  productName,
  optionName,
  price,
  quantity = 1,
  disabled = false,
}: AddToCartButtonProps) {
  const { addItem } = useCart();
  const [showToast, setShowToast] = useState(false);

  // TASK-FE-102: a logged-out visitor adds to the guest cart; it carries over on login.
  const handleClick = useCallback(() => {
    addItem({ productId, variantId, productName, optionName, price }, quantity);
    setShowToast(true);
  }, [
    addItem,
    productId,
    variantId,
    productName,
    optionName,
    price,
    quantity,
  ]);

  const clearToast = useCallback(() => setShowToast(false), []);

  const bgColor = disabled
    ? 'var(--color-text-muted)'
    : 'var(--color-accent)';

  return (
    <>
      {showToast && (
        <Toast message="장바구니에 추가되었습니다." type="success" onClose={clearToast} />
      )}
      <button
        type="button"
        onClick={handleClick}
        disabled={disabled}
        aria-label={disabled ? '품절' : '장바구니 담기'}
        className="btn btn-lg"
        style={{
          width: '100%',
          backgroundColor: bgColor,
          color: 'var(--color-white)',
          border: 'none',
          cursor: disabled ? 'not-allowed' : 'pointer',
          transition: 'background-color var(--transition-normal)',
        }}
      >
        {disabled ? '품절' : '장바구니 담기'}
      </button>
    </>
  );
}
