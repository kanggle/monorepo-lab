import {
  normalizeQuery,
  type FanPublicData,
  type PublicArtist,
  type PublicPost,
  type PublicProduct,
} from '@demo/public-data';

/**
 * 저장본 안에서의 선택 — **순수 함수만** 있다.
 *
 * 🔵 `server-only` 를 안 붙인다. 여기에는 네트워크도 env 도 없고, 그래서 유닛 테스트가
 *    **실제 번들 시드**를 그대로 먹여 검증할 수 있다. 판독자(`api/read.ts`)와 화면 사이에
 *    이 층을 둔 이유가 그것이다 — 시험 가능한 자리를 만든다.
 */

/** 발행일 내림차순. 동률은 id 로 안정화한다(정렬이 불안정하면 페이지네이션이 깨진다). */
function byPublishedAtDesc(a: PublicPost, b: PublicPost): number {
  return b.publishedAt.localeCompare(a.publishedAt) || a.id.localeCompare(b.id);
}

/**
 * 공개 피드에 실릴 글.
 *
 * 🔴🔴 잠긴 글을 **거르지 않는다.** 거르면 방문자는 "이 아티스트는 글이 없다" 로 읽고,
 *    그것은 거짓이다. 잠긴 글은 «있다는 사실 + 제목» 까지만 공개하고 본문은 애초에
 *    봉투에 없다(`PublicPost.body === null` — 발행 계약이 보장한다). 화면은 그 자리에서
 *    "가입하면 볼 수 있다" 를 말할 수 있고, 본문은 HTML·RSC 어디에도 없다.
 */
export function feedPosts(data: FanPublicData): PublicPost[] {
  return data.posts.slice().sort(byPublishedAtDesc);
}

// ---------------------------------------------------------------------------
// 공개 피드 필터 (TASK-FAN-FE-025)
// ---------------------------------------------------------------------------

export type FeedVisibility = PublicPost['visibility'];

/** 필터 `<select>` 의 순서이자 쿼리스트링이 받아들이는 값의 **닫힌** 집합. */
export const FEED_VISIBILITIES: readonly FeedVisibility[] = ['PUBLIC', 'MEMBERS_ONLY', 'PREMIUM'];

/**
 * **실제로 적용되는** 필터. `null` = 그 축은 전체.
 *
 * 🔴 쿼리스트링 원문이 아니라 이 값을 화면(폼의 선택 상태·페이지 링크·0건 문구)에 쓴다.
 *    저장본에 없는 아티스트 id 나 모르는 공개 범위를 받아도 그 축은 무시되고, 폼도
 *    「전체」를 보여준다 — 화면이 걸려 있지 않은 필터를 걸려 있다고 말하지 않게 한다.
 */
export interface FeedFilter {
  artistId: string | null;
  visibility: FeedVisibility | null;
  /** 방문자가 친 검색어(앞뒤 공백만 제거). 정규화 결과가 비면 `null`. */
  q: string | null;
}

export const NO_FEED_FILTER: FeedFilter = { artistId: null, visibility: null, q: null };

export function resolveFeedFilter(
  data: FanPublicData,
  raw: { artist?: string; visibility?: string; q?: string },
): FeedFilter {
  const artistId =
    raw.artist && data.artists.some((a) => a.id === raw.artist) ? raw.artist : null;
  const visibility = FEED_VISIBILITIES.find((v) => v === raw.visibility) ?? null;
  const q = normalizeQuery(raw.q) === '' ? null : (raw.q ?? '').trim();
  return { artistId, visibility, q };
}

export function isFeedFiltered(filter: FeedFilter): boolean {
  return filter.artistId !== null || filter.visibility !== null || filter.q !== null;
}

/**
 * 공개 피드를 거른다. 세 축은 AND 다.
 *
 * 🔴🔴 필터가 없으면 결과는 `feedPosts` 와 **원소·순서가 같다** — 잠긴 글을 기본으로 거르지
 *    않는 위 정책을 그대로 물려받는다. 잠긴 글이 빠지는 것은 방문자가 공개 범위를 «공개» 로
 *    직접 골랐을 때뿐이다.
 *
 * 🔵 검색은 `queryArtists` 와 같은 약한 규칙이다 — `normalizeQuery` 로 정규화한 토큰이
 *    **모두** 제목 또는 아티스트명 안에 부분 문자열로 있으면 매치. 형태소 분석을 흉내내지 않는다.
 *    백엔드 피드(`GET /api/community/feed`)에는 필터 파라미터가 없어 맞춰야 할 백엔드
 *    의미가 없으므로, 이 필터는 공용 `@demo/public-data` 가 아니라 이 앱에 산다.
 */
export function filterFeedPosts(data: FanPublicData, filter: FeedFilter): PublicPost[] {
  const tokens = normalizeQuery(filter.q).split(' ').filter((t) => t !== '');
  return feedPosts(data).filter((p) => {
    if (filter.artistId !== null && p.artistId !== filter.artistId) return false;
    if (filter.visibility !== null && p.visibility !== filter.visibility) return false;
    if (tokens.length > 0) {
      const text = `${p.title} ${p.artistStageName}`.toLowerCase();
      if (!tokens.every((t) => text.includes(t))) return false;
    }
    return true;
  });
}

/**
 * 필터를 보존한 홈 링크. 빈 축은 생략하고, 첫 페이지면 `page` 도 생략한다 — 필터 없는
 * 첫 페이지는 정확히 `/` 다(예전 링크와 같은 모양).
 */
export function feedHref(filter: FeedFilter, page = 0): string {
  const params = new URLSearchParams();
  if (filter.artistId !== null) params.set('artist', filter.artistId);
  if (filter.visibility !== null) params.set('visibility', filter.visibility);
  if (filter.q !== null) params.set('q', filter.q);
  if (page > 0) params.set('page', String(page));
  const qs = params.toString();
  return qs ? `/?${qs}` : '/';
}

export function findArtist(data: FanPublicData, id: string): PublicArtist | null {
  return data.artists.find((a) => a.id === id) ?? null;
}

export function findPost(data: FanPublicData, id: string): PublicPost | null {
  return data.posts.find((p) => p.id === id) ?? null;
}

/** 한 아티스트의 공개 글. 순서는 피드와 같다 — 같은 글이 화면마다 다른 순서로 보이면 안 된다. */
export function artistPosts(data: FanPublicData, artistId: string): PublicPost[] {
  return data.posts.filter((p) => p.artistId === artistId).sort(byPublishedAtDesc);
}

/**
 * 한 아티스트의 공식 굿즈 — 스토어 공개 저장본(`store.json`)에서 **`collectionRef === artistId`** 인 상품
 * (`ADR-MONO-079` D3 · `TASK-MONO-749`). 굿즈 카드(`TASK-MONO-739`)는 이 함수의 결과만 그린다.
 *
 * 🔴🔴 **저장본 안에서만 고른다** — 인자가 이미 읽힌 데이터뿐이라 여기서 백엔드로 갈 길이 없다
 *    (익명 방문 «게이트웨이 호출 0», `ADR-MONO-077` D1).
 * 🔴 매칭은 **id 일치**다. 예명 접두어(옛 739 R4)를 쓰지 않는다 — 예명이 바뀌거나 겹쳐도 안 깨진다.
 * 🔴 `fan` 의 공개 아티스트 목록에 **없는** id 면 빈 배열이다 — 보관(ARCHIVED)·비공개 아티스트는 팬 저장본에서
 *    이미 걸러지므로, 그 아티스트를 가리키는 상품은 팬 어디에도 안 보인다(스토어에는 그대로 있다). `FK 없음`
 *    의 결과인 고아 상품이 화면에 새지 않게 하는 자리가 여기다.
 * 🔵 `collectionRef === null` 인 상품(필드가 생기기 전의 상품 전부)은 어떤 아티스트에도 매칭되지 않는다.
 * 🔵 순서는 저장본 순서 그대로다(같은 데이터면 같은 순서 — 화면이 렌더마다 흔들리지 않는다).
 */
export function artistGoods(
  fan: FanPublicData,
  products: readonly PublicProduct[],
  artistId: string,
): PublicProduct[] {
  if (!fan.artists.some((a) => a.id === artistId)) return [];
  return products.filter((p) => p.collectionRef !== null && p.collectionRef === artistId);
}

/**
 * 페이지 수. `PagedResult` 에는 `totalPages` 가 없고(백엔드 계약에도 없다) 화면의
 * `<Pagination>` 은 그것을 요구하므로, **한 곳에서** 유도한다.
 *
 * 🔵 0건일 때 0 이 아니라 1 을 준다 — `<Pagination>` 은 `totalPages <= 1` 이면 아무것도
 *    안 그리므로 결과는 같지만, "페이지가 0개" 라는 값이 다른 계산으로 새는 것을 막는다.
 */
export function totalPagesOf(totalElements: number, size: number): number {
  if (size <= 0) return 1;
  return Math.max(1, Math.ceil(totalElements / size));
}
