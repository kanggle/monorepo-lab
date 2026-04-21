'use client';

import Image from 'next/image';
import Link from 'next/link';
import { memo, useState } from 'react';
import type { ProductSummary } from '@repo/types';
import { PriceDisplay } from '@/shared/ui';
import styles from './ProductCard.module.css';

interface ProductCardProps {
  product: ProductSummary;
  action?: React.ReactNode;
}

export const ProductCard = memo(function ProductCard({ product, action }: ProductCardProps) {
  const isSoldOut = product.status === 'SOLD_OUT';
  const [imgError, setImgError] = useState(false);

  return (
    <Link
      href={`/products/${product.id}`}
      className={`${styles.card} ${isSoldOut ? styles.cardSoldOut : ''}`}
      style={{ opacity: isSoldOut ? 0.6 : 1 }}
    >
      {product.thumbnailUrl && !imgError ? (
        <div className={styles.imageWrap}>
          <Image
            src={product.thumbnailUrl}
            alt={product.name}
            fill
            sizes="(max-width: 768px) 50vw, 25vw"
            className={styles.image}
            onError={() => setImgError(true)}
            unoptimized={product.thumbnailUrl.includes('placehold.co') || product.thumbnailUrl.startsWith('http://localhost')}
          />
          {action && <div className={styles.action}>{action}</div>}
        </div>
      ) : (
        <div className={styles.placeholder}>
          이미지 없음
          {action && <div className={styles.action}>{action}</div>}
        </div>
      )}
      <div className={styles.body}>
        <h3 className={styles.name}>{product.name}</h3>
        <p className={styles.price}>
          <PriceDisplay amount={product.price} />
        </p>
        {isSoldOut && (
          <span className={styles.soldOutBadge}>
            품절
          </span>
        )}
      </div>
    </Link>
  );
});
