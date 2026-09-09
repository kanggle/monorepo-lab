import { useRef } from 'react';
import type { ProductDetailView } from '@/entities/product';
import type { SelectedItem } from './types';
import { VARIANT_OPTION_TESTID } from './variant-option-testid';
import { useClickOutside } from '@/shared/hooks/use-click-outside';
import styles from './ProductDetailWithCart.module.css';

interface VariantSelectorProps {
  variants: ProductDetailView['variants'];
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
                  /* 🔴 «옵션 항목» 이라는 역할에만 이름을 붙인다 — 재고에 대해서는 아무것도
                     주장하지 않는다. 선택 가능 여부는 아래 `disabled` 하나가 말하고,
                     e2e 헬퍼는 그 둘을 합친 `SELECTABLE_VARIANT_OPTION` 으로 고른다.
                     이 testid 가 필요해진 사유는 `variant-option-testid.ts` 에 한 번만 적었다. */
                  data-testid={VARIANT_OPTION_TESTID}
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
                    {/* 🔴🔴 세 갈래다. `stock === null` 은 «모른다» 이므로 **아무것도 안
                        그린다** — 숫자도, "재고 있음" 같은 배지도. 저장본은 재고를 싣지
                        않고(ADR-MONO-070 § 재고), 모르는 것을 그리면 그 화면이 없는 사실을
                        주장한다. 재고·판매 가능 여부는 상세 상단 문구가 «주문 단계에서
                        확인» 이라고 말하고, 실제 판정은 주문 직전 라이브 검증이 한다. */}
                    {v.stock === null ? null : isSoldOut ? (
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
