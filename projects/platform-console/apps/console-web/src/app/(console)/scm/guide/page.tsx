import { ScmGuideScreen } from '@/features/scm-guide';

/**
 * SCM 가이드 라우트 (TASK-PC-FE-188). scm-platform 도메인 서비스 구성과 3개 운영
 * 화면(개요=발주+재고 가시성 · 보충 · 설정)이 보여주는 값의 정적 참조 화면.
 * IAM 가이드(/iam/guide) · WMS 가이드(/wms/guide) · E-Commerce 가이드
 * (/ecommerce/guide)와 같은 패턴 — 데이터 페치·권한 게이트 없는 순수 정적
 * 화면이므로 `/scm`·`/scm/replenishment` 등 라이브 화면과 달리 `force-dynamic`
 * 이 필요 없다.
 */
export default function ScmGuidePage() {
  return <ScmGuideScreen />;
}
