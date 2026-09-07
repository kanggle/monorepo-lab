import { categoryFacets, queryProducts, type StoreQuery } from '@demo/public-data';
import type { SearchRequest, SearchResponse } from '@repo/types';
import { readStoreSnapshot } from '@/shared/public-data/store-snapshot';
import { toCategoryFacets } from '@/entities/product/api/snapshot-mappers';

/**
 * 공개 상품 검색. **출처는 공개 저장본이다** — 근거는 `entities/product/api/get-products.ts`
 * 머리 주석(«TASK-FE-061 이 지운 조용한 폴백과 무엇이 다른가» 포함).
 *
 * 🔴 검색·필터·정렬의 **의미**는 백엔드와 같은 것을 쓴다: `sort` 값 집합은 `SearchSortOrder`
 *    와 동일하고, 카테고리 패싯은 «현재 질의의 카테고리 조건을 뺀 채로» 센다(백엔드 패싯과
 *    같은 규칙). 구현은 `@demo/public-data` 의 `query.ts` 한 곳뿐이라 화면마다 갈릴 수 없다.
 */
export interface StoreSearchResponse extends SearchResponse {
  /** 🔴 «검색 결과 없음» 과 «저장본이 비었음» 을 가르는 축. get-products.ts 와 같은 뜻. */
  corpusSize: number;
}

export async function searchProducts(params: SearchRequest): Promise<StoreSearchResponse> {
  const { data } = await readStoreSnapshot();

  const query: StoreQuery = {
    q: params.q,
    categoryId: params.categoryId ?? null,
    minPrice: params.minPrice ?? null,
    maxPrice: params.maxPrice ?? null,
    // 백엔드 `SearchRequest.status` 는 자유 문자열이다. 저장본이 아는 두 값만 통과시키고
    // 나머지는 «필터 없음» 으로 둔다(모르는 값을 0건으로 번역하면 오타가 «없음» 이 된다).
    status: params.status === 'ON_SALE' || params.status === 'SOLD_OUT' ? params.status : null,
    sort: params.sort ?? 'relevance',
    page: params.page,
    size: params.size,
  };

  const paged = queryProducts(data.products, query);

  return {
    query: params.q,
    content: paged.content.map((p) => ({
      productId: p.id,
      name: p.name,
      price: p.price,
      status: p.status,
      thumbnailUrl: p.thumbnailUrl ?? '',
      categoryId: p.categoryId,
      // 🔴 점수를 **꾸며내지 않는다.** 백엔드는 Elasticsearch 점수를 매기고 저장본은 못
      //    매긴다(query.ts § relevance). 순위는 정렬로 이미 표현됐고, 이 필드를 그리는
      //    화면은 없다 ⇒ 0 은 "점수 없음" 이지 "관련도 0" 이 아니다.
      score: 0,
    })),
    facets: {
      categories: toCategoryFacets(categoryFacets(data.products, query), data.categories),
      // 🔴 가격 버킷은 **비운다.** 저장본에는 버킷 정의가 없고, 여기서 코퍼스로부터 버킷을
      //    지어내면 백엔드와 다른 규칙으로 같은 이름의 필터를 그리게 된다(같은 질의에 다른
      //    답을 내면서 아무도 그 사실을 모르는 상태). `minPrice`/`maxPrice` 가 URL 에 있으면
      //    필터 자체는 그대로 동작한다 — 안 그리는 것은 **버튼**뿐이다.
      priceRanges: [],
    },
    page: paged.page,
    size: paged.size,
    totalElements: paged.totalElements,
    corpusSize: paged.corpusSize,
  };
}
