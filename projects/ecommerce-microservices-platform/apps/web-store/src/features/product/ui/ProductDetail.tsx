import type { ProductDetail as ProductDetailType } from '@repo/types';
import { ProductImage } from '@/entities/product';
import styles from './ProductDetail.module.css';

interface ProductDetailProps {
  product: ProductDetailType;
}

export function ProductDetail({ product }: ProductDetailProps) {
  return (
    <div className={styles.layout}>
      <ProductImage
        images={product.images?.length ? product.images.map((img) => img.url) : [`/images/products/${product.id}.jpg`]}
        alt={product.name}
      />
      <div className={styles.info}>
        <h1 className={styles.name}>{product.name}</h1>
        <p className={styles.price}>
          {product.price.toLocaleString()}원
        </p>
        <p className={styles.description}>{product.description}</p>

        {product.variants.length > 0 && (
          <div className={styles.variantsSection}>
            <h3 className={styles.variantsTitle}>옵션</h3>
            <ul className={styles.variantList}>
              {product.variants.map((variant) => (
                <li key={variant.id} className={styles.variantItem}>
                  <span>{variant.optionName}</span>
                  <span className={styles.variantMeta}>
                    {variant.additionalPrice > 0 && (
                      <span>+{variant.additionalPrice.toLocaleString()}원</span>
                    )}
                    <span className={variant.stock === 0 ? styles.stockOut : styles.stockIn}>
                      {variant.stock === 0 ? '품절' : `재고 ${variant.stock}`}
                    </span>
                  </span>
                </li>
              ))}
            </ul>
          </div>
        )}
      </div>
    </div>
  );
}
