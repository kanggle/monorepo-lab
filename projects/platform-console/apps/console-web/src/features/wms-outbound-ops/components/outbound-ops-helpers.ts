import { messageForCode } from '@/shared/api/errors';
import type { StatusTone } from '@/shared/ui/StatusBadge';

/**
 * Pure helpers for the wms outbound operations surface (TASK-PC-FE-101 split).
 * Action-kind copy + the status-filter option list + the cancel / TMS-retry
 * producer-error → inline-operator-message maps. No hooks, no JSX.
 */

export type ActionKind = 'pick' | 'pack' | 'ship';

export const STATUS_FILTER_OPTIONS = [
  '',
  'PICKING',
  'PICKED',
  'PACKING',
  'PACKED',
  'SHIPPED',
  'CANCELLED',
  'BACKORDERED',
] as const;

/**
 * Maps an outbound order status to a shared semantic {@link StatusTone}
 * (rendered via the shared `<StatusBadge>` — TASK-PC-FE-158). Unknown / absent
 * / future status → `neutral`, so the console never crashes on a producer enum
 * it does not know (TOLERANCE invariant). The raw status string stays the badge
 * label at the call site, keeping status-text assertions + the status filter in
 * lock-step with the producer enum.
 */
const OUTBOUND_STATUS_TONE: Record<string, StatusTone> = {
  PICKING: 'warning',
  PICKED: 'progress',
  PACKING: 'progress',
  PACKED: 'progress',
  SHIPPED: 'success',
  CANCELLED: 'danger',
  BACKORDERED: 'warning',
};

export function outboundStatusTone(status: string | undefined): StatusTone {
  return status ? (OUTBOUND_STATUS_TONE[status] ?? 'neutral') : 'neutral';
}

export const ACTION_COPY: Record<
  ActionKind,
  { title: string; description: string; confirmLabel: string }
> = {
  pick: {
    title: '피킹을 확정할까요?',
    description:
      '시스템이 계획한 피킹(위치·수량)을 그대로 확정합니다. 콘솔은 위치/수량을 임의로 만들지 않습니다. 이 작업은 멱등하게 한 번만 반영됩니다.',
    confirmLabel: '피킹 확정',
  },
  pack: {
    title: '패킹을 진행할까요?',
    description:
      '주문 라인 전체로 패킹 단위를 생성하고 봉인(seal)합니다. 두 번의 호출(생성 → 봉인)이 각각 멱등하게 처리됩니다. 완료되면 주문이 PACKED 상태가 됩니다.',
    confirmLabel: '패킹 진행',
  },
  ship: {
    title: '출고를 확정할까요?',
    description:
      '출고(shipment)를 확정합니다. 완료되면 주문이 SHIPPED 상태가 되고, 연결된 ecommerce 주문도 출고 완료로 전환됩니다. 이 작업은 멱등하게 한 번만 반영됩니다.',
    confirmLabel: '출고 확정',
  },
};

/** Cancel-specific producer error → inline operator message (§ 1.4 errors). */
export function cancelErrorMessage(code: string): string {
  switch (code) {
    case 'ORDER_ALREADY_SHIPPED':
      return '이미 출고된 주문은 취소할 수 없습니다.';
    case 'STATE_TRANSITION_INVALID':
      return '주문 상태가 변경되어 취소할 수 없습니다. 목록을 새로고침하세요.';
    case 'FORBIDDEN':
      return '취소 권한이 없습니다. 피킹 이후 취소는 관리자(OUTBOUND_ADMIN) 권한이 필요합니다.';
    default:
      return messageForCode(code, '주문을 취소하지 못했습니다.');
  }
}

/** TMS-retry-specific producer error → inline operator message (§ 4.3 +
 *  the proxy's SHIPMENT_NOT_FOUND). */
export function retryTmsErrorMessage(code: string): string {
  switch (code) {
    case 'SHIPMENT_NOT_FOUND':
      return '출고 건을 찾을 수 없습니다. TMS 재전송 대상이 없습니다.';
    case 'STATE_TRANSITION_INVALID':
      return '이미 정상 통보되었거나 재전송 대상 상태가 아닙니다. 목록을 새로고침하세요.';
    case 'FORBIDDEN':
      return 'TMS 재전송 권한이 없습니다. 관리자(OUTBOUND_ADMIN) 권한이 필요합니다.';
    case 'DUPLICATE_REQUEST':
      return '이미 재전송 요청이 접수되었습니다 (중복 무시).';
    default:
      return messageForCode(code, 'TMS 재전송을 처리하지 못했습니다.');
  }
}
