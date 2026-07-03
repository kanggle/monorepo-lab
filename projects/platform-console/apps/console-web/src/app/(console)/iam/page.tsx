import { IamGuideScreen } from '@/features/iam-guide';

/**
 * IAM 개요(가이드) 라우트 (TASK-PC-FE-163). IAM drill 의 첫 진입점 — role
 * 카탈로그 + 화면 접근 매트릭스 + 위임 체인 + 도메인 롤 설명. 정적 화면이라
 * 데이터 페치·권한 게이트가 없다(가이드는 콘솔 진입자 누구나 열람 가능). 다른
 * IAM 화면(계정 운영/운영자 관리/감사)과 달리 `force-dynamic` 이 불필요하다.
 */
export default function IamGuidePage() {
  return <IamGuideScreen />;
}
