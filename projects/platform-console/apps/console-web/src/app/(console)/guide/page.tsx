import { GlobalGuideScreen } from '@/features/global-guide';
import { DEMO_LOGIN_EMAIL } from '@/widgets/demo-credentials/DemoLoginCredentials';

/**
 * 전역 가이드 라우트 (TASK-PC-FE-298). 도메인 가이드들과 같은 패턴 — 데이터 페치·권한
 * 게이트 없는 순수 정적 화면이라 `force-dynamic` 이 필요 없고, 샘플 방문자(ADR-MONO-074)
 * 에게도 백엔드 호출 없이 렌더된다(`coverage.ts` SCREEN_COVERAGE `'/guide': 'static'`).
 *
 * 테스트 계정 이메일은 이 앱의 유일본(`DemoLoginCredentials.tsx` — z11 가드가 대조하는
 * 사본)에서 가져와 props 로 넘긴다. 가이드가 문자열을 한 벌 더 들면 사본이 늘어난다.
 */
export default function GlobalGuidePage() {
  return <GlobalGuideScreen demoLoginEmail={DEMO_LOGIN_EMAIL} />;
}
