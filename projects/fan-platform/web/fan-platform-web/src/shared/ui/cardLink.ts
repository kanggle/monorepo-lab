/**
 * 피드 카드 전체를 상세 링크로 만드는 **stretched link** 클래스 (TASK-FAN-FE-022).
 *
 * 쓰는 법: 카드 루트에 `relative`, 상세로 가는 `<a>` **하나**에 `CARD_LINK_CLASS`,
 * 카드 안의 **다른** 링크에는 `CARD_INNER_LINK_CLASS`.
 *
 * ─────────────────────────────────────────────────────────────────────────
 * 왜 이 모양인가
 * ─────────────────────────────────────────────────────────────────────────
 * 🔴 카드를 통째로 `<Link>` 로 감싸지 않는다 — 카드 안에 이미 링크(아티스트 배지 · 잠긴 카드의
 *    「멤버십 안내 보기」)가 있어 `<a>` 안의 `<a>` 가 된다. HTML 규칙 위반이고 브라우저가 DOM 을
 *    고쳐 쓰면서 클릭이 엉뚱한 곳으로 간다.
 * 🔴 `onClick` + `router.push` 도 안 쓴다 — 키보드로 못 열고, 새 탭 열기(⌘/Ctrl·가운데 클릭)가 깨진다.
 *
 * ⇒ 진짜 `<a>` 하나의 `::after` 를 카드 전체(`inset-0`)에 늘린다.
 *
 * 🔴 `after:z-[1]` — 카드 안의 사진 틀(`PostImage`)은 `relative` 라서, DOM 에서 링크보다 뒤에 오면
 *    덮개 **위로** 그려진다. 그러면 사진을 눌러도 상세로 안 간다. 덮개를 한 칸 올린다.
 * 🔴 `CARD_INNER_LINK_CLASS` 의 `z-10` — 그 덮개보다 위여야 안쪽 링크가 자기 목적지로 간다.
 *    빠뜨려도 href 만 보는 시험은 초록이다(티켓 Failure 3) ⇒ 라이브 브라우저 확인이 AC 다.
 * 🔵 초점 표시는 `::after` 에 링을 그린다 — 덮개가 카드 크기라 카드 전체 테두리로 보인다.
 */
export const CARD_LINK_CLASS =
  'after:absolute after:inset-0 after:z-[1] after:rounded-xl after:content-[""] focus:outline-none focus-visible:after:ring-2 focus-visible:after:ring-brand-500';

/** 카드 안에서 **상세가 아닌** 곳으로 가는 링크 — 덮개 위로 올린다. */
export const CARD_INNER_LINK_CLASS = 'relative z-10';
