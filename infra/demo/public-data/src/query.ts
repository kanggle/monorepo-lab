// DEMO-PUBLIC-DATA: 저장본 안에서의 검색·필터·정렬·페이지 이동.
//
// =============================================================================
// 왜 질의가 여기 있는가 (ADR-MONO-070 § D3.2)
// =============================================================================
// 요구사항이 두 가지를 함께 건다:
//
//   *"실제 스키마에 있는 검색·필터·정렬 의미를 보존한다."*
//   *"저장 범위 밖 결과를 '전체 데이터에 없음' 으로 오인시키지 않는다."*
//
// 앞의 것은 «백엔드가 하던 일을 여기서 같은 뜻으로 한다» 는 뜻이고, 뒤의 것은 «여기가
// 백엔드가 아니라는 사실을 결과가 말해야 한다» 는 뜻이다. 그래서 결과 타입에 `coverage` 가
// 실린다 — 화면이 "저장본 N건 중" 을 말할 수 있어야 0건을 «세상에 없음» 으로 그리지 않는다.
//
// 🔴 이 파일은 **저장본 배열만** 본다. 네트워크도, 백엔드도, EC2 의 검색 서비스도 없다.
//    데이터가 커지면 도는 자리가 서버로 옮겨갈 뿐(그리고 그것이 기본이다) 여기가 하는 일은
//    같다 — 화면은 같은 함수를 서버에서 부른다.
// =============================================================================

import type { PublicArtist, PublicProduct } from './datasets';

export interface PageRequest {
  page?: number;
  size?: number;
}

export interface PagedResult<T> {
  content: T[];
  page: number;
  size: number;
  /** 질의에 걸린 총 건수. */
  totalElements: number;
  /**
   * 🔴 **질의 이전의 모집단 크기.** `totalElements` 가 0 일 때 화면이 «검색 결과 없음» 과
   *    «저장본이 비었다» 를 구별하려면 이 수가 필요하다. 둘은 다른 사실이고 다른 문구를
   *    받아야 한다.
   */
  corpusSize: number;
}

const DEFAULT_SIZE = 20;
const MAX_SIZE = 100;

function normalizePage(req: PageRequest): { page: number; size: number } {
  const page = Number.isFinite(req.page) && (req.page as number) >= 0 ? Math.floor(req.page as number) : 0;
  const rawSize = Number.isFinite(req.size) && (req.size as number) > 0 ? Math.floor(req.size as number) : DEFAULT_SIZE;
  return { page, size: Math.min(rawSize, MAX_SIZE) };
}

export function paginate<T>(rows: T[], req: PageRequest, corpusSize: number): PagedResult<T> {
  const { page, size } = normalizePage(req);
  const start = page * size;
  return {
    content: rows.slice(start, start + size),
    page,
    size,
    totalElements: rows.length,
    corpusSize,
  };
}

/**
 * 검색어 정규화.
 *
 * 🔴 소문자 + 공백 압축까지만 한다. 형태소 분석이나 어간 추출을 **흉내내지 않는다** —
 *    백엔드의 검색 서비스(Elasticsearch 계열)와 다른 규칙을 여기에 넣으면, 이 화면은
 *    「같은 질의에 다른 답」을 내면서 그 사실을 아무도 모른다. 부분 문자열 포함이라는
 *    **약한 규칙**을 쓰고, 화면이 「저장본 안에서 찾습니다」를 말한다.
 */
export function normalizeQuery(q: string | null | undefined): string {
  return (q ?? '').trim().toLowerCase().replace(/\s+/g, ' ');
}

function matchesText(searchText: string, tokens: string[]): boolean {
  if (tokens.length === 0) return true;
  return tokens.every((t) => searchText.includes(t));
}

// ---------------------------------------------------------------------------
// store
// ---------------------------------------------------------------------------

/**
 * 백엔드 `SearchSortOrder` 와 **같은 값 집합**이다
 * (`packages/types/src/search.ts`). 값을 늘리거나 이름을 바꾸지 마라 — 화면의 쿼리스트링이
 * 그대로 이 값이고, 실시간 백엔드로 돌아가는 날 그 문자열이 그쪽으로 간다.
 */
export type StoreSortOrder = 'relevance' | 'price_asc' | 'price_desc' | 'newest';

export interface StoreQuery extends PageRequest {
  q?: string | null;
  categoryId?: string | null;
  minPrice?: number | null;
  maxPrice?: number | null;
  status?: 'ON_SALE' | 'SOLD_OUT' | null;
  sort?: StoreSortOrder | null;
}

/**
 * 🔴 `relevance` 의 의미를 **꾸며내지 않는다.** 백엔드는 점수를 매기고 여기는 못 매긴다.
 *    그래서 `relevance` 는 «질의어가 이름 앞쪽에 있을수록 먼저» 라는 정직하고 설명 가능한
 *    규칙으로 두고, 동점은 이름순으로 안정 정렬한다. 무작위나 삽입 순서로 두면 같은 질의가
 *    페이지를 넘길 때 **원소가 사라지거나 두 번 나온다**(정렬이 불안정하면 페이지네이션이
 *    깨진다 — 이 함수가 안정 정렬을 보장하는 이유).
 */
function relevanceScore(p: PublicProduct, tokens: string[]): number {
  if (tokens.length === 0) return 0;
  const name = p.name.toLowerCase();
  let score = 0;
  for (const t of tokens) {
    const idx = name.indexOf(t);
    if (idx === 0) score -= 100;
    else if (idx > 0) score -= 50 - Math.min(idx, 49);
  }
  return score;
}

export function queryProducts(products: PublicProduct[], query: StoreQuery): PagedResult<PublicProduct> {
  const tokens = normalizeQuery(query.q).split(' ').filter((t) => t !== '');
  let rows = products.filter((p) => {
    if (query.categoryId != null && query.categoryId !== '' && p.categoryId !== query.categoryId) return false;
    if (query.status != null && p.status !== query.status) return false;
    if (query.minPrice != null && Number.isFinite(query.minPrice) && p.price < query.minPrice) return false;
    if (query.maxPrice != null && Number.isFinite(query.maxPrice) && p.price > query.maxPrice) return false;
    return matchesText(p.searchText, tokens);
  });

  const sort: StoreSortOrder = query.sort ?? 'relevance';
  // 🔵 `slice()` 로 복사한 뒤 정렬한다 — 인자로 받은 배열은 **저장본 캐시의 것**이다.
  //    제자리 정렬하면 다음 요청이 다른 순서의 저장본을 본다(그리고 그 버그는 산발적이다).
  rows = rows.slice().sort((a, b) => {
    switch (sort) {
      case 'price_asc':
        return a.price - b.price || a.name.localeCompare(b.name, 'ko');
      case 'price_desc':
        return b.price - a.price || a.name.localeCompare(b.name, 'ko');
      case 'newest':
        return b.createdAt.localeCompare(a.createdAt) || a.name.localeCompare(b.name, 'ko');
      case 'relevance':
      default:
        return relevanceScore(a, tokens) - relevanceScore(b, tokens) || a.name.localeCompare(b.name, 'ko');
    }
  });

  return paginate(rows, query, products.length);
}

/**
 * 카테고리 패싯 — 백엔드 `SearchFacets.categories` 와 같은 뜻.
 * 🔴 **현재 질의의 카테고리 조건을 뺀 채로** 센다. 안 그러면 고른 카테고리만 남아서
 *    "다른 카테고리로 옮기기" 가 화면에서 불가능해진다(백엔드 패싯도 같은 규칙이다).
 */
export function categoryFacets(
  products: PublicProduct[],
  query: StoreQuery,
): Array<{ id: string; count: number }> {
  const withoutCategory: StoreQuery = { ...query, categoryId: null, page: 0, size: MAX_SIZE };
  const tokens = normalizeQuery(withoutCategory.q).split(' ').filter((t) => t !== '');
  const counts = new Map<string, number>();
  for (const p of products) {
    if (withoutCategory.status != null && p.status !== withoutCategory.status) continue;
    if (withoutCategory.minPrice != null && p.price < withoutCategory.minPrice) continue;
    if (withoutCategory.maxPrice != null && p.price > withoutCategory.maxPrice) continue;
    if (!matchesText(p.searchText, tokens)) continue;
    counts.set(p.categoryId, (counts.get(p.categoryId) ?? 0) + 1);
  }
  return [...counts.entries()].map(([id, count]) => ({ id, count })).sort((a, b) => b.count - a.count || a.id.localeCompare(b.id));
}

// ---------------------------------------------------------------------------
// fan
// ---------------------------------------------------------------------------

export interface ArtistQuery extends PageRequest {
  q?: string | null;
}

export function queryArtists(artists: PublicArtist[], query: ArtistQuery): PagedResult<PublicArtist> {
  const tokens = normalizeQuery(query.q).split(' ').filter((t) => t !== '');
  const rows = artists
    .filter((a) => matchesText(a.searchText, tokens))
    .slice()
    .sort((a, b) => a.stageName.localeCompare(b.stageName, 'ko'));
  return paginate(rows, query, artists.length);
}
