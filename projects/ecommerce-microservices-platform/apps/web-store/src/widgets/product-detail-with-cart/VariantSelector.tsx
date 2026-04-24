import { useRef } from 'react';
import type { ProductDetail } from '@repo/types';
import type { SelectedItem } from './types';
import { useClickOutside } from '@/shared/hooks/use-click-outside';
import styles from './ProductDetailWithCart.module.css';

interface VariantSelectorProps {
  variants: ProductDetail['variants'];
  selectedItems: SelectedItem[];
  dropdownOpen: boolean;
  onDropdownToggle: () => void;
  onSelect: (variantId: string) => void;
  onDropdownClose: () => void;
}

export function VariantSelector({
  variants,
  selectedItems,
  dropdownOpen,
  onDropdownToggle,
  onSelect,
  onDropdownClose,
}: VariantSelectorProps) {
  const dropdownRef = useRef<HTMLDivElement>(null);
  useClickOutside(dropdownRef, onDropdownClose);

  return (
    <div>
      <span className={styles.optionLabel}>옵션</span>
      <div className={styles.dropdown} ref={dropdownRef}>
        <button
          type="button"
          className={styles.dropdownTrigger}
          onClick={onDropdownToggle}
        >
          <span className={styles.dropdownPlaceholder}>옵션을 선택하세요</span>
          <span className={`${styles.dropdownArrow} ${dropdownOpen ? styles.dropdownArrowOpen : ''}`}>▾</span>
        </button>
        {dropdownOpen && (
          <div className={styles.dropdownMenu}>
            {variants.map((v) => {
              const isSelected = selectedItems.some((s) => s.variantId === v.id);
              const isSoldOut = v.stock === 0;
              const isDisabled = isSoldOut || isSelected;
              return (
                <button
                  key={v.id}
                  type="button"
                  className={`${styles.dropdownItem} ${isDisabled ? styles.dropdownItemDisabled : ''}`}
                  disabled={isDisabled}
                  onClick={() => onSelect(v.id)}
                >
                  <span className={styles.dropdownItemName}>{v.optionName}</span>
                  <span className={styles.dropdownItemMeta}>
                    {v.additionalPrice > 0 && (
                      <span className={styles.dropdownItemPrice}>
                        +{v.additionalPrice.toLocaleString()}원
                      </span>
                    )}
                    {isSoldOut ? (
                      <span className={styles.dropdownItemSoldOut}>품절</span>
                    ) : (
                      <span className={styles.dropdownItemStock}>재고 {v.stock}</span>
                    )}
                  </span>
                </button>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
