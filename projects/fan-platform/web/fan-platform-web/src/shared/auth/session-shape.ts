/**
 * `auth()` 가 돌려준 값이 «인증된 사용자» 인가 — **순수 술어**.
 *
 * ─────────────────────────────────────────────────────────────────────────
 * 왜 이 술어가 별도 모듈로 나와 있는가 (TASK-FAN-FE-019 의 결함을 다시 안 밟기 위해)
 * ─────────────────────────────────────────────────────────────────────────
 * 원래 이 판정은 `src/middleware.ts` 안에만 있었고, 같은 질문을 하는 두 번째 자리
 * (`shared/auth/session.ts` 의 `isAuthenticated()`)는 `Boolean(session)` 이었다.
 * 두 술어는 **다른 답을 낸다**:
 *
 *   | `auth()` 반환                        | `Boolean(session)` | 이 술어 |
 *   |--------------------------------------|--------------------|---------|
 *   | `null`                               | false              | false   |
 *   | `{ user: {…}, … }`                   | true               | true    |
 *   | `{ user: undefined, … }` (F3 강등)   | **true**           | false   |
 *   | `{ message: "There was a problem…" }`| **true**           | false   |
 *
 * 아래 두 행이 갈리는 자리다. 4행은 auth.js 가 **설정 오류로 500 을 냈을 때의 본문**이고
 * (`next-auth/lib/index.js` 가 `response.ok` 검사 없이 `r.json()` 을 그대로 돌려준다),
 * 그것을 «세션이 있다» 로 읽으면 화면은 로그인한 것처럼 굴면서 게이트웨이를 부른다.
 *
 * 🔴🔴 그 오독이 이 앱에서 뜻하는 바가 새로 하나 더 늘었다: 공개 브라우징이 생기면서
 *    «익명 방문자는 백엔드로 요청을 **하나도** 보내지 않는다» 가 지켜야 할 성질이 됐다.
 *    `Header` 는 이 술어로 알림을 부를지 정하므로, 4행을 true 로 읽는 순간 **익명
 *    방문자가 게이트웨이를 때린다** — 그리고 그 요청은 401 로 조용히 실패해서 아무도
 *    모른다. 그래서 술어를 한 벌로 만들고, 두 소비자가 **같은 함수**를 부른다.
 *
 * 🔵 순수 함수라서 `middleware.ts`(edge 런타임)와 `session.ts`(node 런타임) 양쪽에서
 *    임포트할 수 있다. 여기에 `next-auth`·`next/headers` 를 끌어들이지 마라 — 그 순간
 *    edge 쪽이 못 쓰게 되고, 사본이 다시 생긴다.
 */
export function hasAuthenticatedUser(session: unknown): boolean {
  if (typeof session !== 'object' || session === null) return false;
  return (session as { user?: unknown }).user != null;
}
