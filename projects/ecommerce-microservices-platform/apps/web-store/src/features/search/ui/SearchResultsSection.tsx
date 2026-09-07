import { Suspense } from 'react';
import { LoadingSpinner } from '@repo/ui';
import { Pagination } from '@/shared/ui';
import { SearchBar } from './SearchBar';
import { SearchFilters } from './SearchFilters';
import { SearchResults } from './SearchResults';
import type { ProductSummary, SearchResponse } from '@repo/types';

interface SearchResultsSectionProps {
  /**
   * 🔵 `corpusSize` 는 저장본 경로에서만 실린다(`StoreSearchResponse`). 옵셔널로 둔 이유는
   *    이 컴포넌트가 «어디서 온 결과인가» 를 몰라도 되게 하기 위해서다 — 없으면 «검색 결과
   *    없음» 쪽으로 붙는다(모집단을 모를 때 «저장본이 비었다» 고 단정하는 것이 더 나쁘다).
   */
  result: SearchResponse & { corpusSize?: number };
  searchParams: Record<string, string>;
  /** 카드 액션 주입점 — 그대로 `SearchResults` 로 전달한다 (계층 근거는 그쪽 주석). */
  renderAction?: (product: ProductSummary) => React.ReactNode;
  /**
   * 출처 표시 주입점. 🔴 `renderAction` 과 **같은 이유**로 프롭이다 — 출처 위젯은
   * `widgets/` 에 살고, feature 가 위젯을 직접 import 하면 계층이 뒤집힌다. 두 계층을 다 아는
   * app 레이어가 넘긴다.
   */
  provenance?: React.ReactNode;
}

export function SearchResultsSection({ result, searchParams, renderAction, provenance }: SearchResultsSectionProps) {
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
      <div style={{ display: 'flex', alignItems: 'baseline', gap: 'var(--space-3)', marginBottom: 'var(--space-4)' }}>
        <p style={{ color: 'var(--color-text-secondary)', margin: 0, fontSize: 'var(--font-size-sm)' }}>
          &quot;{result.query}&quot; 검색 결과 {result.totalElements}건
        </p>
        {provenance}
      </div>
      <SearchResults
        items={result.content}
        query={result.query}
        corpusSize={result.corpusSize}
        renderAction={renderAction}
      />
      <div style={{ marginTop: 'var(--space-8)' }}>
        <Pagination currentPage={result.page} totalElements={result.totalElements} pageSize={result.size} baseHref="/products" searchParams={searchParams} />
      </div>
    </div>
  );
}
