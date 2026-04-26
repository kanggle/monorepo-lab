'use client';

import { useMemo } from 'react';
import Link from 'next/link';
import type { ProductDetail } from '@repo/types';
import { ProductImage } from '@/entities/product';
import { WishlistButton } from '@/features/wishlist';
import { Toast } from '@/shared/ui';
import { VariantSelector } from './VariantSelector';
import { SelectedItemsList } from './SelectedItemsList';
import { PurchaseSummary } from './PurchaseSummary';
import { useProductVariantSelection } from './use-product-variant-selection';
import styles from './ProductDetailWithCart.module.css';

interface ProductDetailWithCartProps {
  product: ProductDetail;
}

export function ProductDetailWithCart({ product }: ProductDetailWithCartProps) {
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

  const images = useMemo(
    () => {
      if (!product.images?.length) return [`/images/products/${product.id}.jpg`];
      return product.images.map((img) => img.url);
    },
    [product.images, product.id],
  );

  return (
    <>
      {showToast && (
        <Toast message="장바구니에 추가되었습니다." type="success" onClose={clearToast} />
      )}
      <nav className={styles.breadcrumb}>
        <Link href="/">홈</Link>
        <span className={styles.breadcrumbSep}>&rsaquo;</span>
        <Link href="/products">상품</Link>
        <span className={styles.breadcrumbSep}>&rsaquo;</span>
        <span className={styles.breadcrumbCurrent}>{product.name}</span>
      </nav>

      <div className={styles.layout}>
        <ProductImage
          images={images}
          alt={product.name}
        />

        <div className={styles.info}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 'var(--space-2)' }}>
            <h1 className={styles.name} style={{ margin: 0 }}>{product.name}</h1>
            <WishlistButton productId={product.id} size="md" />
          </div>
          <p className={styles.basePrice}>
            {product.price.toLocaleString()}
            <span className={styles.basePriceUnit}>원</span>
          </p>
          <p className={styles.description}>{product.description}</p>

          <div className={styles.divider} />

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
        </div>
      </div>
    </>
  );
}
