import { queryProducts, categoryFacets, type StoreQuery } from '@demo/public-data';
import type { CategoryFacet, PaginatedResponse, ProductListParams, ProductSummary, SearchSortOrder } from '@repo/types';
import { readStoreSnapshot } from '@/shared/public-data/store-snapshot';
import { toCategoryFacets, toProductSummary } from './snapshot-mappers';

/**
 * 공개 상품 목록. **출처는 공개 저장본이다** (ADR-MONO-070 / `shared/public-data/store-snapshot.ts`).
 *
 * 🔴🔴 **TASK-FE-061 이 지운 «조용한 목 폴백» 과 혼동하지 마라.** 그때 지워진 것은
 *    *백엔드 호출이 실패했을 때 말없이 가짜 상품으로 갈아끼우던* 코드였다. 여기 있는 것은
 *    **선언된 1차 출처**다 — 백엔드를 먼저 부르고 실패 시 떨어지는 사슬이 아니라(이 모듈에는
 *    게이트웨이로 가는 코드가 아예 없다), 화면이 `DataProvenanceNotice` 로 **출처를 말하며**
 *    읽는 저장본이다. 그리고 저장본의 id 는 실제 UUID 라, 그때 문제였던 «`mock-1` 이
 *    위시리스트·장바구니·주문 쓰기 경로를 터뜨린다» 도 성립하지 않는다.
 *    ⇒ 되돌리려면 그때의 근거가 아니라 **이쪽 축**에 반박해야 한다.
 *
 * 🔵 시그니처는 기존 호출부와 호환된다: 인자는 `ProductListParams` 를 넓힌 것이고, 반환은
 *    `PaginatedResponse<ProductSummary>` 에 **더 얹은** 것이다(빼지 않았다).
 */
export interface StoreListParams extends ProductListParams {
  /** 검색어. 🔵 백엔드 목록 API 의 `name` 과 같은 자리이며, 없으면 `name` 을 쓴다. */
  q?: string;
  minPrice?: number;
  maxPrice?: number;
  /** 🔴 값 집합은 백엔드 `SearchSortOrder` 와 **같다**. 늘리거나 이름을 바꾸지 마라. */
  sort?: SearchSortOrder;
}

export interface StoreProductListResult extends PaginatedResponse<ProductSummary> {
  /**
   * 🔴 **질의 이전의** 저장본 크기. `totalElements === 0` 일 때 화면이 «조건에 맞는 게 없다»
   *    와 «저장본이 비었다» 를 구별하는 유일한 축이다. 둘은 다른 사실이고, 뒤엣것을 앞엣것으로
   *    그리면 화면이 "전체 카탈로그가 비었다" 는 **없는 주장**을 한다.
   */
  corpusSize: number;
  /** 카테고리 필터 UI 가 그리는 목록(현재 질의의 카테고리 조건을 뺀 채로 센다). */
  categories: CategoryFacet[];
}

/**
 * `ProductListParams.status` 는 `HIDDEN` 을 허용하지만 저장본에는 `HIDDEN` 이 **없다**
 * (발행자가 거른다 — `datasets.ts` § PublicProduct). 그래서 `HIDDEN` 요청은 «항상 0건» 이
 * 정답이고, 그것을 «필터 없음» 으로 번역하면 숨긴 상품을 요청했는데 전체가 나온다.
 */
function toSnapshotStatus(status: ProductListParams['status']): StoreQuery['status'] | 'HIDDEN' {
  if (status === 'ON_SALE' || status === 'SOLD_OUT') return status;
  if (status === 'HIDDEN') return 'HIDDEN';
  return null;
}

export async function getProducts(params?: StoreListParams): Promise<StoreProductListResult> {
  const { data } = await readStoreSnapshot();
  const status = toSnapshotStatus(params?.status);

  if (status === 'HIDDEN') {
    return {
      content: [],
      page: params?.page ?? 0,
      size: params?.size ?? 20,
      totalElements: 0,
      corpusSize: data.products.length,
      categories: [],
    };
  }

  const query: StoreQuery = {
    q: params?.q ?? params?.name ?? null,
    categoryId: params?.categoryId ?? null,
    minPrice: params?.minPrice ?? null,
    maxPrice: params?.maxPrice ?? null,
    status,
    sort: params?.sort ?? null,
    page: params?.page,
    size: params?.size,
  };

  const paged = queryProducts(data.products, query);

  return {
    content: paged.content.map(toProductSummary),
    page: paged.page,
    size: paged.size,
    totalElements: paged.totalElements,
    corpusSize: paged.corpusSize,
    categories: toCategoryFacets(categoryFacets(data.products, query), data.categories),
  };
}
