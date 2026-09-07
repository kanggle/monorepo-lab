import type { FanPublicData, PublicArtist, PublicPost } from '@demo/public-data';

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
