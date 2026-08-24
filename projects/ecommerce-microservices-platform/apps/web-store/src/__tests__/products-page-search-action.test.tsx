import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import type { SearchResponse } from '@repo/types';

// 이 스위트가 잡는 결함(TASK-FE-099): `/products` 는 카드마다 위시리스트 하트를 달지만
// `/products?q=…`(검색 결과)는 안 달았다 — 같은 상품인데 검색해서 찾으면 목록에서 찜을
// 못 했다. 판정 지점을 페이지로 잡은 이유는 배선이 끊기는 자리가 여기이기 때문이다:
// SearchResults/SearchResultsSection 쪽 테스트는 프롭을 "받으면" 쓰는지만 말할 수 있고,
// 페이지가 애초에 안 넘기면 둘 다 초록인 채로 화면에서 하트가 사라진다.

vi.mock('@/entities/product', () => ({
  getProducts: vi.fn(),
}));

vi.mock('@/features/search', () => ({
  searchProducts: vi.fn(),
  SearchBar: () => <div data-testid="search-bar" />,
  SearchResultsSection: ({
    result,
    renderAction,
  }: {
    result: SearchResponse;
    renderAction?: (product: { id: string }) => React.ReactNode;
  }) => (
    <div data-testid="search-results-section">
      {result.content.map((item) => (
        <span key={item.productId}>{renderAction?.({ id: item.productId })}</span>
      ))}
    </div>
  ),
}));

vi.mock('@/features/wishlist', () => ({
  WishlistButton: ({ productId }: { productId: string }) => (
    <button type="button" data-testid="wishlist-button" data-product-id={productId} />
  ),
}));

vi.mock('@/widgets/product-list-with-wishlist', () => ({
  ProductListWithWishlist: () => <div data-testid="product-list-with-wishlist" />,
}));

vi.mock('@/shared/ui', () => ({
  Pagination: () => <div data-testid="pagination" />,
}));

vi.mock('@repo/ui', () => ({
  ErrorMessage: ({ message }: { message: string }) => <div data-testid="error-message">{message}</div>,
  LoadingSpinner: () => <div data-testid="loading-spinner" />,
}));

import { searchProducts } from '@/features/search';
import { getProducts } from '@/entities/product';
import ProductsPage from '@/app/(store)/products/page';

const mockSearchProducts = vi.mocked(searchProducts);
const mockGetProducts = vi.mocked(getProducts);

const searchResult: SearchResponse = {
  query: '노트북',
  content: [
    { productId: 'p1', name: '노트북 A', price: 1000000, status: 'ON_SALE', thumbnailUrl: '/a.jpg', categoryId: 'c1', score: 1.5 },
    { productId: 'p2', name: '노트북 B', price: 2000000, status: 'ON_SALE', thumbnailUrl: '/b.jpg', categoryId: 'c1', score: 1.2 },
  ],
  facets: { categories: [], priceRanges: [] },
  page: 0,
  size: 20,
  totalElements: 2,
};

describe('ProductsPage — 검색 결과 카드 액션', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('검색 결과 카드마다 위시리스트 버튼을 주입한다', async () => {
    mockSearchProducts.mockResolvedValue(searchResult);

    render(await ProductsPage({ searchParams: Promise.resolve({ q: '노트북' }) }));

    const buttons = screen.getAllByTestId('wishlist-button');
    expect(buttons).toHaveLength(2);
    expect(buttons.map((el) => el.getAttribute('data-product-id'))).toEqual(['p1', 'p2']);
  });

  it('검색어가 없으면 전체 상품 목록(위시리스트 포함)을 그린다', async () => {
    mockGetProducts.mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0 });

    render(await ProductsPage({ searchParams: Promise.resolve({}) }));

    expect(screen.getByTestId('product-list-with-wishlist')).toBeInTheDocument();
    expect(screen.queryByTestId('search-results-section')).not.toBeInTheDocument();
  });
});
