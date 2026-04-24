import type { SearchProductItem } from '@repo/types';
import { ProductCard } from '@/entities/product';
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
    <div
      style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))',
        gap: 'var(--space-6)',
      }}
    >
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
    </div>
  );
}
