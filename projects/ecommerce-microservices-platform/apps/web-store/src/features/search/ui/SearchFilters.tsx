'use client';

import { useRouter, useSearchParams } from 'next/navigation';
import type { CategoryFacet, PriceRangeFacet } from '@repo/types';

interface SearchFiltersProps {
  categories?: CategoryFacet[];
  priceRanges?: PriceRangeFacet[];
}

/**
 * 개방 구간(null)까지 커버하는 가격 버킷 라벨.
 *
 * - min=null, max=X → "X원 이하"
 * - min=X,    max=null → "X원 이상"
 * - 둘 다 null → "전체" (비정상 입력 방어)
 * - 둘 다 있음 → "X~Y원"
 */
function formatPriceRange(min: number | null, max: number | null): string {
  if (min == null && max == null) return '전체';
  if (min == null) return `${max!.toLocaleString()}원 이하`;
  if (max == null) return `${min.toLocaleString()}원 이상`;
  return `${min.toLocaleString()}~${max.toLocaleString()}원`;
}

export function SearchFilters({ categories, priceRanges }: SearchFiltersProps) {
  const router = useRouter();
  const searchParams = useSearchParams();

  function updateParam(key: string, value: string | undefined) {
    const params = new URLSearchParams(searchParams.toString());
    if (value) {
      params.set(key, value);
    } else {
      params.delete(key);
    }
    params.delete('page');
    router.push(`/products?${params.toString()}`);
  }

  const currentCategoryId = searchParams.get('categoryId') ?? undefined;
  const currentSort = searchParams.get('sort') ?? 'relevance';

  return (
    <div style={{ display: 'flex', gap: 'var(--space-3)', flexWrap: 'wrap', alignItems: 'center' }}>
      {categories && categories.length > 0 && (
        <select
          aria-label="카테고리 필터"
          value={currentCategoryId ?? ''}
          onChange={(e) => updateParam('categoryId', e.target.value || undefined)}
          className="input"
          style={{ width: 'auto', padding: 'var(--space-2) var(--space-3)' }}
        >
          <option value="">전체 카테고리</option>
          {categories.map((cat) => (
            <option key={cat.id} value={cat.id}>
              {cat.name ?? '기타'} ({cat.count})
            </option>
          ))}
        </select>
      )}

      {priceRanges && priceRanges.length > 0 && (
        <div style={{ display: 'flex', gap: 'var(--space-2)', alignItems: 'center' }}>
          {priceRanges.map((range) => (
            <button
              key={`${range.min ?? 'min'}-${range.max ?? 'max'}`}
              onClick={() => {
                const params = new URLSearchParams(searchParams.toString());
                if (range.min != null) params.set('minPrice', String(range.min));
                else params.delete('minPrice');
                if (range.max != null) params.set('maxPrice', String(range.max));
                else params.delete('maxPrice');
                params.delete('page');
                router.push(`/products?${params.toString()}`);
              }}
              className="btn"
              style={{ fontSize: 'var(--font-size-xs)', padding: 'var(--space-1) var(--space-2)' }}
            >
              {formatPriceRange(range.min, range.max)} ({range.count})
            </button>
          ))}
        </div>
      )}

      <select
        aria-label="정렬 기준"
        value={currentSort}
        onChange={(e) => updateParam('sort', e.target.value)}
        className="input"
        style={{ width: 'auto', padding: 'var(--space-2) var(--space-3)' }}
      >
        <option value="relevance">관련도순</option>
        <option value="price_asc">낮은 가격순</option>
        <option value="price_desc">높은 가격순</option>
        <option value="newest">최신순</option>
      </select>
    </div>
  );
}
