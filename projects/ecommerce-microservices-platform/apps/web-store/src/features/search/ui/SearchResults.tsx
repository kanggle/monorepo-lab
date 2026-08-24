import type { ProductStatus, ProductSummary, SearchProductItem } from '@repo/types';
import { ProductCard } from '@/entities/product';
import { ProductGrid } from '@/shared/ui/ProductGrid';
import { EmptyState } from '@repo/ui';

interface SearchResultsProps {
  items: SearchProductItem[];
  query: string;
  /**
   * 카드마다 붙일 액션 (위시리스트 하트 등). `ProductList` 과 같은 시그니처다.
   * 액션은 feature 가 아니라 두 계층을 다 아는 app 레이어에서 주입한다 —
   * 여기서 위젯을 직접 import 하면 feature → widget 으로 계층이 뒤집힌다.
   */
  renderAction?: (product: ProductSummary) => React.ReactNode;
}

function toProductSummary(item: SearchProductItem): ProductSummary {
  return {
    id: item.productId,
    name: item.name,
    price: item.price,
    status: item.status as ProductStatus,
    thumbnailUrl: item.thumbnailUrl,
    categoryId: item.categoryId,
  };
}

export function SearchResults({ items, query, renderAction }: SearchResultsProps) {
  if (items.length === 0) {
    return <EmptyState message={`"${query}"에 대한 검색 결과가 없습니다.`} />;
  }

  return (
    <ProductGrid>
      {items.map((item) => {
        const product = toProductSummary(item);
        return (
          <ProductCard
            key={product.id}
            product={product}
            action={renderAction?.(product)}
          />
        );
      })}
    </ProductGrid>
  );
}
