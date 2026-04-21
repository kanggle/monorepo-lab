import type { ProductSummary } from '@repo/types';
import { ProductCard } from '@/entities/product';
import { EmptyState } from '@repo/ui';
import styles from './ProductList.module.css';

interface ProductListProps {
  products: ProductSummary[];
  renderAction?: (product: ProductSummary) => React.ReactNode;
}

export function ProductList({ products, renderAction }: ProductListProps) {
  if (products.length === 0) {
    return <EmptyState message="등록된 상품이 없습니다." />;
  }

  return (
    <div className={styles.grid}>
      {products.map((product) => (
        <ProductCard
          key={product.id}
          product={product}
          action={renderAction?.(product)}
        />
      ))}
    </div>
  );
}
