import { Suspense } from 'react';
import { LoadingSpinner } from '@repo/ui';
import { Pagination } from '@/shared/ui';
import { SearchBar } from './SearchBar';
import { SearchFilters } from './SearchFilters';
import { SearchResults } from './SearchResults';
import type { SearchResponse } from '@repo/types';

interface SearchResultsSectionProps {
  result: SearchResponse;
  searchParams: Record<string, string>;
}

export function SearchResultsSection({ result, searchParams }: SearchResultsSectionProps) {
  return (
    <div className="container" style={{ paddingTop: 'var(--space-8)', paddingBottom: 'var(--space-16)' }}>
      <div style={{ marginBottom: 'var(--space-6)' }}>
        <Suspense fallback={<LoadingSpinner />}><SearchBar /></Suspense>
      </div>
      <div style={{ marginBottom: 'var(--space-4)' }}>
        <Suspense fallback={<LoadingSpinner />}>
          <SearchFilters categories={result.facets.categories} priceRanges={result.facets.priceRanges} />
        </Suspense>
      </div>
      <p style={{ color: 'var(--color-text-secondary)', marginBottom: 'var(--space-4)', fontSize: 'var(--font-size-sm)' }}>
        &quot;{result.query}&quot; 검색 결과 {result.totalElements}건
      </p>
      <SearchResults items={result.content} query={result.query} />
      <div style={{ marginTop: 'var(--space-8)' }}>
        <Pagination currentPage={result.page} totalElements={result.totalElements} pageSize={result.size} baseHref="/products" searchParams={searchParams} />
      </div>
    </div>
  );
}
