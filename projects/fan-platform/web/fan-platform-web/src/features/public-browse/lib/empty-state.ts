/**
 * 0건의 **두 가지 뜻**을 가른다.
 *
 * ─────────────────────────────────────────────────────────────────────────
 * 왜 이 함수가 있는가
 * ─────────────────────────────────────────────────────────────────────────
 * 요구가 명시한다: *"저장 범위 밖 결과를 '전체 데이터에 없음' 으로 오인시키지 않는다."*
 * 목록이 0건일 때 그 0 은 서로 **다른 두 사실** 중 하나다:
 *
 *   · corpusSize > 0  — 저장본에는 자료가 있는데 **이 질의에 안 걸렸다.** 방문자가 할 일은
 *                       검색어를 바꾸는 것이다.
 *   · corpusSize == 0 — **저장본 자체가 비어 있다.** 검색어를 아무리 바꿔도 안 나온다.
 *                       방문자가 할 일은 없고, 이건 운영 쪽 상태다.
 *
 * 두 칸에 같은 문구를 쓰면 두 번째 경우의 방문자가 검색어를 바꿔 가며 헤매고, 끝내
 * "이 서비스에는 아티스트가 없다" 로 읽는다. `PagedResult.corpusSize` 는 정확히 이
 * 구별을 위해 존재한다(`@demo/public-data/src/query.ts` 의 같은 주석).
 *
 * 🔵 `null` 은 «비어 있지 않다» 다 — 호출자가 `if (kind)` 로 분기할 수 있게 한다.
 * ─────────────────────────────────────────────────────────────────────────
 */
export type EmptyKind = 'no-match' | 'empty-corpus';

export interface CountedResult {
  totalElements: number;
  corpusSize: number;
}

export function emptyKind({ totalElements, corpusSize }: CountedResult): EmptyKind | null {
  if (totalElements > 0) return null;
  return corpusSize > 0 ? 'no-match' : 'empty-corpus';
}
