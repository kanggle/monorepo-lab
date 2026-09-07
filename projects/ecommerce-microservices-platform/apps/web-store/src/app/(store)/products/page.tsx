export const revalidate = 60;

import { Suspense } from 'react';
import { getProducts } from '@/entities/product';
import { searchProducts, SearchBar, SearchFilters, SearchResultsSection } from '@/features/search';
import { ProductListWithWishlist } from '@/widgets/product-list-with-wishlist';
import { DataProvenanceNotice } from '@/widgets/data-provenance';
import { WishlistButton } from '@/features/wishlist';
import { Pagination, SnapshotEmptyState } from '@/shared/ui';
import { ErrorMessage, LoadingSpinner } from '@repo/ui';
import type { SearchSortOrder } from '@repo/types';

interface Props {
  searchParams: Promise<{
    q?: string;
    categoryId?: string;
    minPrice?: string;
    maxPrice?: string;
    sort?: string;
    page?: string;
    size?: string;
  }>;
}

/**
 * 쿼리 파라미터 이름과 의미는 **바뀌지 않았다** — `q` / `categoryId` / `minPrice` /
 * `maxPrice` / `sort` / `page` / `size` 그대로다. 바뀐 것은 그 값을 누가 해석하느냐뿐이고
 * (게이트웨이 → 공개 저장본), `sort` 의 값 집합은 백엔드 `SearchSortOrder` 와 **같다**.
 * ⇒ 저장해 둔 URL·북마크·뒤로가기가 그대로 산다.
 */
function toSortOrder(raw: string | undefined): SearchSortOrder | undefined {
  return raw === 'relevance' || raw === 'price_asc' || raw === 'price_desc' || raw === 'newest'
    ? raw
    : undefined;
}

export default async function ProductsPage({ searchParams }: Props) {
  const params = await searchParams;
  const page = Number(params.page ?? '0');
  const size = Number(params.size ?? '20');
  const query = params.q?.trim();

  const searchResult = query
    ? await searchProducts({
        q: query,
        categoryId: params.categoryId,
        minPrice: params.minPrice ? Number(params.minPrice) : undefined,
        maxPrice: params.maxPrice ? Number(params.maxPrice) : undefined,
        sort: toSortOrder(params.sort) ?? 'relevance',
        page,
        size,
      }).catch(() => null)
    : null;

  if (searchResult) {
    // 카드 액션은 여기서 주입한다 — 전체 상품 목록이 `ProductListWithWishlist` 로 받는 것과
    // 같은 하트다. feature 가 위젯을 직접 import 하면 계층이 뒤집히므로 app 레이어가 넘긴다.
    return (
      <SearchResultsSection
        result={searchResult}
        searchParams={params as Record<string, string>}
        renderAction={(product) => <WishlistButton productId={product.id} />}
        provenance={<DataProvenanceNotice />}
      />
    );
  }

  const searchFailed = !!query && !searchResult;

  try {
    const result = await getProducts({
      q: undefined,
      categoryId: params.categoryId,
      minPrice: params.minPrice ? Number(params.minPrice) : undefined,
      maxPrice: params.maxPrice ? Number(params.maxPrice) : undefined,
      sort: toSortOrder(params.sort),
      page,
      size,
    });

    return (
      <div className="container" style={{ paddingTop: 'var(--space-8)', paddingBottom: 'var(--space-16)' }}>
        <div style={{ marginBottom: 'var(--space-6)' }}>
          <Suspense fallback={<LoadingSpinner />}><SearchBar /></Suspense>
        </div>
        {searchFailed && (
          <div
            style={{
              padding: 'var(--space-3) var(--space-4)',
              marginBottom: 'var(--space-4)',
              backgroundColor: '#fff3cd',
              border: '1px solid #ffc107',
              borderRadius: 'var(--radius-md)',
              color: '#856404',
              fontSize: 'var(--font-size-sm)',
            }}
          >
            검색을 사용할 수 없어 전체 상품을 표시합니다.
          </div>
        )}
        <h1 className="page-title">전체 상품</h1>
        {/* 🔵 카테고리 필터는 여기가 **처음**이다 — 예전엔 `categoryId` 파라미터만 있고
            고를 목록이 없어서, 그 파라미터를 아는 사람만 쓸 수 있었다. 저장본이 카테고리
            배열(표시명 + 건수)을 싣기 때문에 이제 그릴 수 있다. 정렬 셀렉트도 같은
            `SearchFilters` 가 이미 갖고 있어 검색 결과 화면과 **한 벌**로 유지된다. */}
        <div style={{ margin: 'var(--space-4) 0' }}>
          <Suspense fallback={<LoadingSpinner />}>
            <SearchFilters categories={result.categories} />
          </Suspense>
        </div>
        <div style={{ marginBottom: 'var(--space-4)' }}>
          <DataProvenanceNotice />
        </div>
        {result.content.length === 0 ? (
          <SnapshotEmptyState corpusSize={result.corpusSize} />
        ) : (
          <ProductListWithWishlist products={result.content} />
        )}
        <div style={{ marginTop: 'var(--space-8)' }}>
          <Pagination currentPage={result.page} totalElements={result.totalElements} pageSize={result.size} baseHref="/products" searchParams={params as Record<string, string>} />
        </div>
      </div>
    );
  } catch {
    return (
      <div className="container" style={{ paddingTop: 'var(--space-8)', paddingBottom: 'var(--space-16)' }}>
        <Suspense fallback={<LoadingSpinner />}><SearchBar /></Suspense>
        <ErrorMessage message="상품 목록을 불러오는 데 실패했습니다." />
      </div>
    );
  }
}
