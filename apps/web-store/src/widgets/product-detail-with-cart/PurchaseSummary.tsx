import styles from './ProductDetailWithCart.module.css';

interface PurchaseSummaryProps {
  totalPrice: number;
  totalQuantity: number;
  canAdd: boolean;
  onAddToCart: () => void;
  onBuyNow: () => void;
}

export function PurchaseSummary({
  totalPrice,
  totalQuantity,
  canAdd,
  onAddToCart,
  onBuyNow,
}: PurchaseSummaryProps) {
  return (
    <div className={styles.purchaseSummary}>
      <div className={styles.priceRow}>
        <span className={styles.priceRowLabel}>
          총 금액{totalQuantity > 0 ? ` (${totalQuantity}개)` : ''}
        </span>
        <span>
          <span className={styles.totalPrice}>
            {totalPrice.toLocaleString()}
          </span>
          <span className={styles.totalPriceUnit}>원</span>
        </span>
      </div>
      <div style={{ display: 'flex', gap: 'var(--space-2)' }}>
        <button
          type="button"
          onClick={onAddToCart}
          disabled={!canAdd}
          className={styles.cartBtn}
          style={{ flex: 1 }}
        >
          {canAdd ? '장바구니 담기' : '옵션을 선택하세요'}
        </button>
        <button
          type="button"
          onClick={onBuyNow}
          disabled={!canAdd}
          className={styles.buyNowBtn}
          style={{ flex: 1 }}
        >
          즉시 주문
        </button>
      </div>
    </div>
  );
}
