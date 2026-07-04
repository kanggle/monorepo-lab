import { EcommerceGuideScreen } from '@/features/ecommerce-guide';

/**
 * E-Commerce 가이드 라우트 (TASK-PC-FE-184). 도메인 서비스 구성과 7개 운영 화면
 * (상품·주문·배송·프로모션·사용자·셀러·알림)이 보여주는 상태값의 정적 참조 화면.
 * IAM 가이드(/iam/guide)·WMS 가이드(/wms/guide)와 같은 패턴 — 데이터 페치·권한
 * 게이트 없는 순수 정적 화면이므로 `/ecommerce/products` 등 라이브 화면과 달리
 * `force-dynamic` 이 필요 없다.
 */
export default function EcommerceGuidePage() {
  return <EcommerceGuideScreen />;
}
