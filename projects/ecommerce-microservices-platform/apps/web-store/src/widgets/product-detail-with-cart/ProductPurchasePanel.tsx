'use client';

import type { ProductDetail } from '@repo/types';
import { Toast } from '@/shared/ui';
import { VariantSelector } from './VariantSelector';
import { SelectedItemsList } from './SelectedItemsList';
import { PurchaseSummary } from './PurchaseSummary';
import { useProductVariantSelection } from './use-product-variant-selection';
import styles from './ProductDetailWithCart.module.css';

interface ProductPurchasePanelProps {
  product: ProductDetail;
}

/**
 * Interactive purchase surface for the product detail page (TASK-FE-081
 * server/client split). This is the ONLY client island of the former
 * monolithic `ProductDetailWithCart` — it owns the cart-selection state
 * (`useProductVariantSelection`) and the variant dropdown / selected-items
 * / purchase buttons / add-to-cart toast. The static scaffold around it
 * (breadcrumb, image, name, price, description) now renders on the server.
 *
 * The toast is `position: fixed` (Toast.tsx), so mounting it here instead of
 * at the page top is visually identical.
 */
export function ProductPurchasePanel({ product }: ProductPurchasePanelProps) {
  const {
    selectedItems,
    dropdownOpen,
    variantMap,
    totalQuantity,
    totalPrice,
    canAdd,
    showToast,
    handleSelect,
    handleRemove,
    handleQuantity,
    handleDropdownToggle,
    handleDropdownClose,
    handleAddToCart,
    handleBuyNow,
    clearToast,
  } = useProductVariantSelection(product);

  return (
    <>
      {showToast && (
        <Toast message="장바구니에 추가되었습니다." type="success" onClose={clearToast} />
      )}

      {product.variants.length > 0 && (
        <div className={styles.optionSection}>
          <VariantSelector
            variants={product.variants}
            selectedItems={selectedItems}
            dropdownOpen={dropdownOpen}
            onDropdownToggle={handleDropdownToggle}
            onSelect={handleSelect}
            onDropdownClose={handleDropdownClose}
          />

          <SelectedItemsList
            selectedItems={selectedItems}
            variantMap={variantMap}
            basePrice={product.price}
            onQuantityChange={handleQuantity}
            onRemove={handleRemove}
          />
        </div>
      )}

      <PurchaseSummary
        totalPrice={totalPrice}
        totalQuantity={totalQuantity}
        canAdd={canAdd}
        onAddToCart={handleAddToCart}
        onBuyNow={handleBuyNow}
      />
    </>
  );
}
