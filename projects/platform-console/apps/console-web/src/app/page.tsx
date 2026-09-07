import { redirect } from 'next/navigation';
import { isAuthenticated } from '@/shared/lib/session';

/**
 * `/` 의 착지점은 **방문자가 누구냐**에 따라 갈린다.
 *
 * 로그인한 운영자
 *   → `(console)/dashboards/overview` — 5도메인 통합 Operator Overview
 *     (TASK-PC-FE-011 / ADR-MONO-017 § D8; TASK-PC-FE-034 가 콘솔 랜딩으로 승격).
 *     **이 경로는 오늘과 한 글자도 다르지 않다.** 인증된 사용자에게 무엇이 바뀌었는지
 *     묻는다면 답은 «아무것도» 다.
 *
 * 익명 방문자
 *   → `/demo` — 공개 둘러보기(샘플·합성 데이터, 로그인 불필요).
 *
 * 🔴🔴 **왜 이 분기가 필요한가.** 예전에는 `/` 가 무조건 `/dashboards/overview` 로 갔고,
 *    익명이면 그 다음 `(console)` 레이아웃의 가드가 `/login?redirect=…` 로 튕겼다. 즉
 *    포트폴리오 방문자가 이 앱에서 **처음 보는 화면이 로그인 폼**이었다. 콘솔이 무엇을
 *    하는 물건인지 보여 줄 자리가 없었던 것이다.
 *
 * 🔴 그렇다고 «`(console)` 을 공개로 연다» 가 답이 될 수는 없다 — 그 화면들은 실제 고객·
 *    주문·재무·계정 데이터를 그린다. 그래서 **경로를 하나 더 만들었지, 가드를 건드리지
 *    않았다.** `(console)/layout.tsx` 의 첫 줄은 그대로이고, 65개 보호 화면의 도달
 *    조건도 그대로다.
 *
 * 🔵 판정에 쓰는 것은 기존 헬퍼 {@link isAuthenticated} 그대로다 — 접근 토큰 **과**
 *    운영자 토큰이 **둘 다** 있어야 참이다(반쪽 authed 상태는 인증이 아니다,
 *    `shared/lib/session.ts` § isAuthenticated). 새 판정 규칙을 여기서 만들지 않는다:
 *    만들면 «인증» 의 정의가 두 곳에 생기고 한쪽만 고쳐진다.
 *
 * 🔵 쿠키를 읽으므로 이 라우트는 정적으로 구울 수 없다. `force-dynamic` 을 명시해
 *    그 사실을 빌드 로그가 아니라 **소스에서** 읽히게 한다.
 */
export const dynamic = 'force-dynamic';

export default async function RootIndex() {
  if (await isAuthenticated()) redirect('/dashboards/overview');
  redirect('/demo');
}
