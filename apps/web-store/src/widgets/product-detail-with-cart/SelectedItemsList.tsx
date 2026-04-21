import type { ProductDetail } from '@repo/types';
import type { SelectedItem } from './types';
import styles from './ProductDetailWithCart.module.css';

interface SelectedItemsListProps {
  selectedItems: SelectedItem[];
  variantMap: Map<string, ProductDetail['variants'][number]>;
  basePrice: number;
  onQuantityChange: (variantId: string, next: number) => void;
  onRemove: (variantId: string) => void;
}

export function SelectedItemsList({
  selectedItems,
  variantMap,
  basePrice,
  onQuantityChange,
  onRemove,
}: SelectedItemsListProps) {
  if (selectedItems.length === 0) return null;

  return (
    <div className={styles.selectedList}>
      {selectedItems.map((item) => {
        const v = variantMap.get(item.variantId);
        if (!v) return null;
        const unitPrice = basePrice + v.additionalPrice;
        return (
          <div key={item.variantId} className={styles.selectedItem}>
            <span className={styles.selectedItemName}>{v.optionName}</span>
            <div className={styles.stepper}>
              <button
                type="button"
                className={styles.stepperBtn}
                onClick={() => onQuantityChange(item.variantId, item.quantity - 1)}
                disabled={item.quantity <= 1}
                aria-label="수량 줄이기"
              >
                −
              </button>
              <span className={styles.stepperValue}>{item.quantity}</span>
              <button
                type="button"
                className={styles.stepperBtn}
                onClick={() => onQuantityChange(item.variantId, item.quantity + 1)}
                disabled={item.quantity >= v.stock}
                aria-label="수량 늘리기"
              >
                +
              </button>
            </div>
            <span className={styles.selectedItemPrice}>
              {(unitPrice * item.quantity).toLocaleString()}원
            </span>
            <button
              type="button"
              className={styles.selectedItemRemove}
              onClick={() => onRemove(item.variantId)}
              aria-label={`${v.optionName} 삭제`}
            >
              ✕
            </button>
          </div>
        );
      })}
    </div>
  );
}
