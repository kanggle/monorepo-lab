'use client';

import { useCallback, useState } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import { useCart } from '../model/cart-context';
import { useAuth } from '@/shared/lib/auth-context';
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
  const { isAuthenticated } = useAuth();
  const router = useRouter();
  const pathname = usePathname();
  const [showToast, setShowToast] = useState(false);

  const handleClick = useCallback(() => {
    if (!isAuthenticated) {
      const redirect = encodeURIComponent(pathname ?? '/');
      router.push(`/login?redirect=${redirect}`);
      return;
    }
    addItem({ productId, variantId, productName, optionName, price }, quantity);
    setShowToast(true);
  }, [
    isAuthenticated,
    pathname,
    router,
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
