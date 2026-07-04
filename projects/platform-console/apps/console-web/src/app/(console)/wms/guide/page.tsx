import { WmsGuideScreen } from '@/features/wms-guide';

/**
 * WMS 가이드 라우트 (TASK-PC-FE-183). 재고(수량 버킷·예약 흐름·저재고·읽기모델)
 * 와 출고(주문 상태머신·TMS 통보·사가)의 정적 참조 화면. IAM 가이드(/iam/guide)
 * 와 같은 패턴 — 데이터 페치·권한 게이트 없는 순수 정적 화면이므로 `/wms/inventory`
 * 등 라이브 화면과 달리 `force-dynamic` 이 필요 없다.
 */
export default function WmsGuidePage() {
  return <WmsGuideScreen />;
}
