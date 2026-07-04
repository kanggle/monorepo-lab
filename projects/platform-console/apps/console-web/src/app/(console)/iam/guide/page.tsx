import { IamGuideScreen } from '@/features/iam-guide';

/**
 * IAM 가이드 라우트 (TASK-PC-FE-180 — relocated from `/iam`, formerly PC-FE-163).
 * The static RBAC reference — role 카탈로그 + 화면 접근 매트릭스 + 위임 체인 +
 * 운영자 온보딩 축 + 도메인 롤. A pure static screen (no data fetch / permission
 * gate — the guide is open to any console entrant), so unlike the live `/iam`
 * overview it needs no `force-dynamic`.
 */
export default function IamGuidePage() {
  return <IamGuideScreen />;
}
