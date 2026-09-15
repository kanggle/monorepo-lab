import { redirect } from 'next/navigation';

/**
 * `/` 는 **누구든** `/dashboards/overview` 로 간다 (`ADR-MONO-074` A8 — `TASK-PC-FE-282`).
 *
 * 로그인한 운영자
 *   → 5도메인 통합 Operator Overview (TASK-PC-FE-011 / ADR-MONO-017 § D8;
 *     TASK-PC-FE-034 가 콘솔 랜딩으로 승격). **이 경로는 예전과 한 글자도 다르지 않다.**
 *
 * 익명 방문자
 *   → **같은 주소**의 실제 개요 화면. 값은 샘플이다 — `(console)` 셸이 샘플 방문자를
 *     들여보내고(`shared/lib/session.ts` § isSampleVisitor), 백엔드로 나가야 할 호출은
 *     게이트웨이 코어가 샘플 라우터의 응답으로 바꾼다(`shared/api/sample-gate.ts`).
 *
 * 🔴 예전에는 여기서 방문자를 가렸다(익명 → `/demo`). 그 분기는 «익명은 `(console)` 에
 *    들어올 수 없다» 는 전제 위에 있었고, `ADR-MONO-074` 가 그 전제를 «익명은 들어오지만
 *    백엔드에 닿을 수 없다» 로 바꿨다. 그래서 착지점을 가를 이유가 사라졌다 —
 *    판정은 이제 이 파일이 아니라 `(console)` 레이아웃과 코어가 한다.
 *
 * 🔵 `/demo` 는 이 변경 뒤에도 남는다(은퇴는 `TASK-MONO-686`). 링크가 사라졌을 뿐이다.
 *
 * 🔵 쿠키를 읽지 않지만 `force-dynamic` 을 유지한다 — 이 리다이렉트가 빌드 산출물에
 *    정적으로 구워지면 `(console)` 쪽의 쿠키별 분기를 우회하는 캐시가 하나 더 생긴다.
 */
export const dynamic = 'force-dynamic';

export default async function RootIndex() {
  redirect('/dashboards/overview');
}
