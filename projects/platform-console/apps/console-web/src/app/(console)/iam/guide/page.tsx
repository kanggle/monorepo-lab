import { IamGuideScreen } from '@/features/iam-guide';

/**
 * IAM 가이드 라우트 (TASK-PC-FE-180 — relocated from `/iam`, formerly PC-FE-163;
 * 3부 재구성 TASK-PC-FE-238). 개념(계정 4가지 상황 · 권한 두 종류) → 메뉴 사용법
 * (메뉴별 작업·게이트 · 온보딩 흐름 · 도달 범위 3축) → 레퍼런스(role 카탈로그 ·
 * 접근 매트릭스 · 권한 키 · 도메인 롤) 로 분리된 정적 참조 화면. A pure static
 * screen (no data fetch / permission gate — the guide is open to any console
 * entrant), so unlike the live `/iam` overview it needs no `force-dynamic`.
 */
export default function IamGuidePage() {
  return <IamGuideScreen />;
}
