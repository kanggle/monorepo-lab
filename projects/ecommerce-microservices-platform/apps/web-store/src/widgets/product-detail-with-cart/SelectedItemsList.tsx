import type { ProductDetailView } from '@/entities/product';
import { MAX_ORDER_QUANTITY } from '@/entities/product';
import type { SelectedItem } from './types';
import styles from './ProductDetailWithCart.module.css';

interface SelectedItemsListProps {
  selectedItems: SelectedItem[];
  variantMap: Map<string, ProductDetailView['variants'][number]>;
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
                // 🔴🔴 `item.quantity >= v.stock` 로 쓰면 안 된다: `stock` 이 `null`(모름)일 때
                //    JS 관계 연산이 null 을 **0 으로 강제**해서 `1 >= 0` → 항상 참이 되고,
                //    `+` 버튼이 영구히 죽는다. "모름" 을 "0" 으로 읽는 바로 그 실패다.
                //    모를 때의 상한은 재고가 아니라 UI 상한(`MAX_ORDER_QUANTITY`)이다.
                disabled={item.quantity >= (v.stock ?? MAX_ORDER_QUANTITY)}
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
