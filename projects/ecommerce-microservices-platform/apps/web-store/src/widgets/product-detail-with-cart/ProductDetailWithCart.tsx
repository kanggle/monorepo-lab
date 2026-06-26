import Link from 'next/link';
import type { ProductDetail } from '@repo/types';
import { ProductImage } from '@/entities/product';
import { WishlistButton } from '@/features/wishlist';
import { ProductPurchasePanel } from './ProductPurchasePanel';
import styles from './ProductDetailWithCart.module.css';

interface ProductDetailWithCartProps {
  product: ProductDetail;
}

/**
 * Product detail surface. **Server Component** (TASK-FE-081 server/client
 * split): the static scaffold — breadcrumb, gallery, name, price, description
 * — renders on the server, and only the interactive purchase surface is the
 * `<ProductPurchasePanel>` client island. `WishlistButton` and `ProductImage`
 * remain their own client islands rendered inline.
 *
 * Previously the whole widget was `'use client'`, so the breadcrumb/heading/
 * price/description markup hydrated needlessly; now they don't.
 */
export function ProductDetailWithCart({ product }: ProductDetailWithCartProps) {
  const images = product.images?.length
    ? product.images.map((img) => img.url)
    : [`/images/products/${product.id}.jpg`];

  return (
    <>
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

          <ProductPurchasePanel product={product} />
        </div>
      </div>
    </>
  );
}
