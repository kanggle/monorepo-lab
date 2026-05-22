import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { SearchResultsSection } from '@/features/search/ui/SearchResultsSection';
import type { SearchResponse } from '@repo/types';

vi.mock('@repo/ui', () => ({
  LoadingSpinner: () => <div data-testid="loading-spinner" />,
}));

vi.mock('@/features/search/ui/SearchBar', () => ({
  SearchBar: () => <div data-testid="search-bar" />,
}));

vi.mock('@/features/search/ui/SearchFilters', () => ({
  SearchFilters: () => <div data-testid="search-filters" />,
}));

vi.mock('@/features/search/ui/SearchResults', () => ({
  SearchResults: ({ items, query }: { items: unknown[]; query: string }) => (
    <div data-testid="search-results" data-count={items.length} data-query={query} />
  ),
}));

vi.mock('@/shared/ui', () => ({
  Pagination: ({ currentPage, totalElements, pageSize }: { currentPage: number; totalElements: number; pageSize: number }) => (
    <div data-testid="pagination" data-page={currentPage} data-total={totalElements} data-size={pageSize} />
  ),
}));

const baseResult: SearchResponse = {
  query: '노트북',
  content: [
    { productId: 'p1', name: '노트북 A', price: 1000000, status: 'ON_SALE', thumbnailUrl: '/img.jpg', categoryId: 'cat-1', score: 1.0 },
  ],
  facets: {
    categories: [],
    priceRanges: [],
  },
  page: 0,
  size: 20,
  totalElements: 1,
};

describe('SearchResultsSection', () => {
  it('검색 결과 건수를 표시한다', () => {
    render(<SearchResultsSection result={baseResult} searchParams={{ q: '노트북' }} />);

    expect(screen.getByText(/검색 결과 1건/)).toBeInTheDocument();
  });

  it('검색어를 표시한다', () => {
    render(<SearchResultsSection result={baseResult} searchParams={{ q: '노트북' }} />);

    expect(screen.getByText(/노트북/)).toBeInTheDocument();
  });

  it('SearchBar를 렌더링한다', () => {
    render(<SearchResultsSection result={baseResult} searchParams={{ q: '노트북' }} />);

    expect(screen.getByTestId('search-bar')).toBeInTheDocument();
  });

  it('SearchFilters를 렌더링한다', () => {
    render(<SearchResultsSection result={baseResult} searchParams={{ q: '노트북' }} />);

    expect(screen.getByTestId('search-filters')).toBeInTheDocument();
  });

  it('SearchResults에 결과를 전달한다', () => {
    render(<SearchResultsSection result={baseResult} searchParams={{ q: '노트북' }} />);

    const results = screen.getByTestId('search-results');
    expect(results).toHaveAttribute('data-count', '1');
    expect(results).toHaveAttribute('data-query', '노트북');
  });

  it('Pagination을 렌더링한다', () => {
    render(<SearchResultsSection result={baseResult} searchParams={{ q: '노트북' }} />);

    const pagination = screen.getByTestId('pagination');
    expect(pagination).toHaveAttribute('data-page', '0');
    expect(pagination).toHaveAttribute('data-total', '1');
  });
});
