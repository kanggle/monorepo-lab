/**
 * 앱 안 이동 이력 — «뒤로» 를 눌렀을 때 브라우저 뒤로가 **이 사이트 안**에 머무는가 (TASK-FAN-FE-023).
 *
 * =============================================================================
 * 🔴🔴 왜 `document.referrer` 도 `history.length` 도 답이 아닌가
 * =============================================================================
 * · `document.referrer` — Next 의 화면 안 이동은 이 값을 **안 바꾼다**. `/artists/{id}` 로 직접
 *   들어와 카드를 눌러 상세로 가면 referrer 는 여전히 빈 값이라, «앱 안에서 왔다» 를 못 본다.
 * · `history.length` — 탭 전체의 이력 길이다. 검색 결과에서 들어왔어도 2 이상이라, 그것만 믿고
 *   `history.back()` 을 부르면 방문자를 **이전 사이트로** 내보낸다.
 *
 * ⇒ 이 앱이 직접 본 경로를 스택으로 기록한다. 스택은 모듈 메모리라 **전체 새로고침·새 탭에서
 *   비어서 시작한다** — 즉 «직접 진입» 이면 판정이 저절로 «이력 없음» 이 된다.
 *
 * 🔴 틀릴 때는 **안전한 쪽으로** 틀린다. 새 경로가 바로 전전 경로와 같으면 브라우저 뒤로로 보고
 *    꺼낸다 — 링크로 같은 곳에 되돌아간 경우도 꺼내지만, 그 결과는 «이력을 적게 센다» 이고
 *    «뒤로» 가 `/` 로 가는 것뿐이다. 반대 방향(많이 세서 사이트 밖으로 나감)은 `history.length`
 *    를 함께 요구해서 한 번 더 막는다.
 * 🔵 경로(pathname)만 본다 — `?page=2` 같은 쿼리 이동은 같은 화면이다.
 */
let stack: string[] = [];

export function recordPathname(pathname: string): void {
  if (stack[stack.length - 1] === pathname) return;
  if (stack.length >= 2 && stack[stack.length - 2] === pathname) {
    stack.pop();
    return;
  }
  stack.push(pathname);
}

export function hasInAppHistory(): boolean {
  if (stack.length < 2) return false;
  return typeof window !== 'undefined' && window.history.length > 1;
}

/** 시험 전용 — 칸마다 «새 탭» 에서 시작한다. */
export function __resetInAppHistory(): void {
  stack = [];
}
