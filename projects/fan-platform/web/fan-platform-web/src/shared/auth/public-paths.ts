/**
 * 로그인 없이 열리는 경로 — **허용 목록**이다.
 *
 * ─────────────────────────────────────────────────────────────────────────
 * 왜 제외 목록이 아니라 허용 목록인가
 * ─────────────────────────────────────────────────────────────────────────
 * 방향이 뒤집히면 성질도 뒤집힌다. 「이 경로들만 막는다」로 짜면 **새 라우트가 기본으로
 * 공개**되고, 그것이 개인 데이터를 그리는 라우트인 날 아무 가드도 안 문다. 여기 적힌
 * 것만 열리고 나머지는 **적지 않아서** 닫힌다 — `/me`, `/compose`, `/notifications`,
 * `/membership/history` 는 물론 **아직 존재하지 않는 라우트**까지.
 *
 * 🔴🔴 `/membership` 은 **정확 일치**다. 접두사로 쓰면 `/membership/history` 가 같이
 *    열리는데, 그 화면은 *내* 결제 이력이다. 「공개 소개」와 「개인 이력」이 한 접두사를
 *    공유하는 것이 이 목록에서 가장 위험한 칸이고, 그래서 그 한 칸만 다른 규칙을 쓴다.
 *    (`middleware-public-paths.test.ts` 가 두 방향을 다 단언한다.)
 *
 * 🔴 `/artists` `/posts` 는 하위 경로까지 열리지만 **`/` 로 끊어서** 본다. 단순
 *    `startsWith('/artists')` 는 `/artists-admin` 같은 미래의 라우트도 열어 준다.
 *
 * 🔵 이 목록이 여는 화면들은 전부 `@demo/public-data` 저장본만 읽는다(게이트웨이를 안
 *    부른다). 즉 «열려 있다» 가 «백엔드가 익명 요청을 받는다» 를 뜻하지 않는다 — 두
 *    사실이 분리돼 있는 것이 이 설계의 요점이다.
 */

/** 하위 경로까지 공개인 접두사. `/x` 자신과 `/x/...` 만 매치한다. */
const PUBLIC_PREFIXES = ['/artists', '/posts'] as const;

/** 그 경로 **자신만** 공개. 하위 경로는 보호된다. */
const PUBLIC_EXACT = ['/', '/membership', '/favicon.ico'] as const;

/**
 * 프레임워크·인증 인프라 경로. 하위 경로 전체가 열린다.
 *
 * 🔵 `/build-info.json` 은 여기 없다 — `middleware.ts` 의 `matcher` 가 애초에 제외하므로
 *    미들웨어가 돌지 않는다(TASK-MONO-600). 두 곳에 적으면 한쪽만 고쳐진다.
 */
const INFRA_PREFIXES = ['/login', '/api/auth', '/_next'] as const;

export function isPublicPath(pathname: string): boolean {
  for (const exact of PUBLIC_EXACT) {
    if (pathname === exact) return true;
  }
  for (const prefix of INFRA_PREFIXES) {
    if (pathname.startsWith(prefix)) return true;
  }
  for (const prefix of PUBLIC_PREFIXES) {
    if (pathname === prefix || pathname.startsWith(`${prefix}/`)) return true;
  }
  return false;
}
