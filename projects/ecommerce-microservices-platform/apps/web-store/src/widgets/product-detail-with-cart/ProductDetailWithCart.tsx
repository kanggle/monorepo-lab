import Link from 'next/link';
import { ProductImage } from '@/entities/product';
import type { ProductDetailView } from '@/entities/product';
import { WishlistButton } from '@/features/wishlist';
import { ProductPurchasePanel } from './ProductPurchasePanel';
import styles from './ProductDetailWithCart.module.css';

interface ProductDetailWithCartProps {
  /**
   * 🔵 `ProductDetail`(라이브)도 그대로 들어온다 — `ProductDetailView` 는 재고 축에서만
   *    넓힌 타입이라 `number` 가 `number | null` 자리에 대입된다.
   */
  product: ProductDetailView;
  /**
   * 이 상세가 **공개 저장본**에서 왔는가.
   *
   * 🔴🔴 참이면 화면은 두 가지를 **말해야** 한다: 가격은 «표시 가격» 이고, 재고·판매 가능
   *    여부는 주문 단계에서 확인된다는 것. 저장본에는 재고가 아예 없고(ADR-MONO-070 § 재고)
   *    가격은 발행 시점의 값이다 — 아무 말도 안 하면 방문자는 그 둘을 «지금 값» 으로 읽는다.
   */
  fromSnapshot?: boolean;
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
export function ProductDetailWithCart({ product, fromSnapshot = false }: ProductDetailWithCartProps) {
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
          {fromSnapshot && (
            // 🔴 재고 «숫자» 도, "재고 있음" 배지도 여기 없다 — 저장본이 그것을 모르기
            //    때문이다. 모르는 것을 문구로 정직하게 말하는 자리다.
            <p data-testid="snapshot-price-note" className={styles.description} style={{ fontSize: 'var(--font-size-sm)', color: 'var(--color-text-muted)' }}>
              표시 가격입니다. 재고·판매 가능 여부는 주문 단계에서 확인됩니다.
            </p>
          )}
          <p className={styles.description}>{product.description}</p>

          <div className={styles.divider} />

          <ProductPurchasePanel product={product} />
        </div>
      </div>
    </>
  );
}
