import type { SearchProductItem } from '@repo/types';
import { ProductCard } from '@/entities/product';
import { ProductGrid } from '@/shared/ui/ProductGrid';
import { EmptyState } from '@repo/ui';

interface SearchResultsProps {
  items: SearchProductItem[];
  query: string;
}

export function SearchResults({ items, query }: SearchResultsProps) {
  if (items.length === 0) {
    return <EmptyState message={`"${query}"에 대한 검색 결과가 없습니다.`} />;
  }

  return (
    <ProductGrid>
      {items.map((item) => (
        <ProductCard
          key={item.productId}
          product={{
            id: item.productId,
            name: item.name,
            price: item.price,
            status: item.status as 'ON_SALE' | 'SOLD_OUT' | 'HIDDEN',
            thumbnailUrl: item.thumbnailUrl,
            categoryId: item.categoryId,
          }}
        />
      ))}
    </ProductGrid>
  );
}
