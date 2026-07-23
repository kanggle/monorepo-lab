/**
 * WMS 가이드 화면의 정적 참조 데이터 (TASK-PC-FE-183).
 *
 * WMS 콘솔의 두 라이브 화면 — **재고(재고 현황, `/wms/inventory`)**와
 * **출고(택배/출고 + 출고 운영, `/wms/outbound`)** — 이 실제로 보여주는 값의
 * 의미를 운영자에게 설명한다. IAM 가이드(`features/iam-guide/data.ts`,
 * TASK-PC-FE-163)와 같은 원칙: 타입 있는 정적 배열 + 정적 화면. 데이터 페치·권한
 * 게이트 없음(콘솔 진입자 누구나 열람).
 *
 * **SoT** (드리프트 시 이 파일 카피도 동반 갱신):
 *   - 재고 수량/예약: `projects/wms-platform/apps/inventory-service` —
 *     `domain-model.md`(`on_hand = available + reserved + damaged` 파생),
 *     `state-machines/reservation-status.md`(RESERVED→CONFIRMED/RELEASED),
 *     이벤트 `specs/contracts/events/inventory-events.md`.
 *   - 재고 읽기모델: `admin-service` `InventorySnapshotEntity` /
 *     `InventoryProjectionService`(콘솔이 읽는 `/dashboard/inventory`).
 *   - 출고 상태머신: `outbound-service`
 *     `state-machines/{order-status,saga-status}.md` +
 *     `domain/model/{OrderStatus,SagaStatus,TmsStatus}.java`.
 *
 * 테스트(WmsGuideScreen.test.tsx)는 섹션/행 존재 등 **구조만** 단언하며, 설명
 * 텍스트 자체는 사람이 스펙과 맞춘다(iam-guide data.ts 동일 정책).
 */

import type {
  GlossaryEntry,
  GuideRecipeData,
} from '@/shared/ui/guide-primitives';

// ───────────────────────── 재고 (Inventory) ─────────────────────────

/** 재고 수량 버킷. `보유(on-hand)`는 저장값이 아니라 파생값. */
export interface StockBucket {
  key: string;
  /** 콘솔 재고 테이블 컬럼 라벨(한글). */
  label: string;
  /** 도메인 필드명(참조용). */
  field: string;
  /** 픽업(출고 할당) 가능 여부. */
  pickable: boolean;
  desc: string;
}

/**
 * 콘솔 재고 현황 테이블의 수량 컬럼. 불변식:
 * `보유(onHand) = 가용(available) + 예약(reserved) + 손상(damaged)` — 보유는
 * 파생값(저장 안 함). 모든 버킷 ≥ 0, 음수로 만드는 연산은 `INSUFFICIENT_STOCK`
 * 로 거부된다.
 */
export const STOCK_BUCKETS: StockBucket[] = [
  {
    key: 'available',
    label: '가용',
    field: 'available_qty',
    pickable: true,
    desc: '새 출고(피킹)에 즉시 할당 가능한 자유 수량. 예약이 잡히면 이만큼 줄고 예약으로 옮겨간다.',
  },
  {
    key: 'reserved',
    label: '예약',
    field: 'reserved_qty',
    pickable: false,
    desc: '활성 예약(Reservation)에 이미 잡혀 있으나 아직 출고 확정되지 않은 수량. 출고 확정 시 소진, 취소/만료 시 가용으로 복귀.',
  },
  {
    key: 'damaged',
    label: '손상',
    field: 'damaged_qty',
    pickable: false,
    desc: '격리·판매 불가 재고. 물리적으로 창고에 있어 보유에는 포함되지만 픽업 대상은 아니다.',
  },
  {
    key: 'onHand',
    label: '보유',
    field: 'on_hand (파생)',
    pickable: false,
    desc: '창고에 물리적으로 존재하는 총량 = 가용 + 예약 + 손상. 저장하지 않고 세 버킷의 합으로 계산한다.',
  },
];

/** 예약 lifecycle 단계 — 가용↔예약 이동을 유발하는 흐름. */
export interface ReservationStage {
  step: string;
  /** 상태/이벤트(참조용). */
  trigger: string;
  /** 버킷 이동. */
  effect: string;
}

/**
 * 예약(Reservation) 상태머신: `RESERVED → CONFIRMED`(확정·종료) 또는
 * `RESERVED → RELEASED`(해제·종료). 재활성 없음. 예약 흐름은 전적으로 출고
 * 이벤트가 구동한다.
 */
export const RESERVATION_STAGES: ReservationStage[] = [
  {
    step: '예약 (RESERVED)',
    trigger: '출고 피킹 요청(outbound.picking.requested) → 재고 예약',
    effect: '가용 −qty · 예약 +qty (가용→예약). 재고 부족이면 예약 실패(inventory.reserve.failed)로 출고가 이월(BACKORDERED).',
  },
  {
    step: '확정 (CONFIRMED)',
    trigger: '출고 확정(outbound.shipping.confirmed) → 예약 소진',
    effect: '예약 −qty (가용은 이미 예약 시점에 빠졌으므로 불변). 종료 상태.',
  },
  {
    step: '해제 (RELEASED)',
    trigger: '출고 취소(outbound.picking.cancelled) · TTL 만료(기본 24h) · 수동 해제',
    effect: '예약 −qty · 가용 +qty (예약→가용 복귀). 사유: CANCELLED · EXPIRED · MANUAL. 종료 상태.',
  },
];

/** 재고를 변동시키는 이벤트(모두 `wms.inventory.*.v1`). */
export interface InventoryEvent {
  event: string;
  label: string;
  desc: string;
}

export const INVENTORY_EVENTS: InventoryEvent[] = [
  {
    event: 'inventory.received',
    label: '입고',
    desc: '입고 적치(inbound.putaway.completed) 결과로 가용 증가.',
  },
  {
    event: 'inventory.adjusted',
    label: '조정',
    desc: '사유 코드가 붙은 수동 보정(실사·분실·발견·손상표시·손상폐기·재분류). 한 버킷을 ±.',
  },
  {
    event: 'inventory.transferred',
    label: '이동',
    desc: '같은 창고 내 두 위치 간 가용 재고 원자적 이동(반출/반입 2 레그).',
  },
  {
    event: 'inventory.reserved',
    label: '예약',
    desc: '출고 할당 — 가용→예약.',
  },
  {
    event: 'inventory.released',
    label: '해제',
    desc: '예약 반환 — 예약→가용(취소·만료·수동).',
  },
  {
    event: 'inventory.confirmed',
    label: '확정',
    desc: '출고가 예약 재고를 소진 — 예약 감소.',
  },
];

/**
 * 저재고(저재고) — **두 개의 다른 메커니즘**이며 임계값이 다르다. 가이드에서
 * 반드시 구분: 테이블 배지와 운영자 알림이 서로 불일치할 수 있다.
 */
export interface LowStockMechanism {
  where: string;
  threshold: string;
  desc: string;
}

export const LOW_STOCK_MECHANISMS: LowStockMechanism[] = [
  {
    where: '재고 테이블 "저재고" 배지 / "저재고만" 필터',
    threshold: '고정 · 가용 ≤ 10',
    desc: 'admin-service 읽기모델이 투영 시점에 고정 임계(10)로 계산해 저장하는 플래그. SKU별 재주문점이 아니다.',
  },
  {
    where: '운영자 저재고 알림(inventory.low-detected)',
    threshold: '설정형 · (창고,SKU) 임계 → 전역 기본',
    desc: 'inventory-service가 변동 트랜잭션 안에서 설정된 임계(가용 < 임계) 도달 시 발행. 임계 미설정이면 감지 비활성.',
  },
];

/**
 * 읽기모델 최종 일관성 안내. 콘솔 재고/출고 표는 이벤트로 투영된 **읽기모델**
 * (admin-service `admin_inventory_snapshot` / `admin_shipment_summary`)이라
 * 쓰기 시스템 대비 잠시 과거일 수 있다.
 */
export const READ_MODEL_NOTE = {
  title: '읽기모델과 지연 배너',
  body: '콘솔 재고·출고 표는 쓰기 시스템(inventory/outbound-service)이 아니라, 그 이벤트를 투영한 admin-service 읽기모델을 읽는다(최종 일관성). 투영 지연이 5초를 넘으면 응답에 지연 헤더가 실리고 화면 상단에 "표시값이 잠시 과거일 수 있습니다" 배너가 뜬다 — 값은 정상이며 곧 수렴한다.',
} as const;

// ───────────────────────── 출고 (Outbound) ─────────────────────────

/** 주문(Order) 애그리거트 상태. */
export interface OrderState {
  name: string;
  label: string;
  terminal: boolean;
  desc: string;
}

/**
 * 주문 상태머신 (8 상태). 정상 경로 6단계
 * `RECEIVED → PICKING → PICKED → PACKING → PACKED → SHIPPED` +
 * 예외 종료 2개(CANCELLED · BACKORDERED). SHIPPED/CANCELLED/BACKORDERED 는 종료.
 */
export const ORDER_STATES: OrderState[] = [
  {
    name: 'RECEIVED',
    label: '접수',
    terminal: false,
    desc: '주문 접수(웹훅/수동). v1에선 같은 트랜잭션에서 즉시 PICKING으로 진행되어 실제로 머무는 걸 볼 일은 없다.',
  },
  {
    name: 'PICKING',
    label: '피킹 중',
    terminal: false,
    desc: '피킹 요청됨(재고 예약 진행). 이 시점부터 주문 라인 불변. 취소 가능.',
  },
  {
    name: 'PICKED',
    label: '피킹 완료',
    terminal: false,
    desc: '전 라인 피킹 확정. 패킹 대기.',
  },
  {
    name: 'PACKING',
    label: '패킹 중',
    terminal: false,
    desc: '포장 유닛 1개 이상 생성(아직 미봉인/미완).',
  },
  {
    name: 'PACKED',
    label: '패킹 완료',
    terminal: false,
    desc: '전 유닛 봉인 + 전 라인 충족. 출고 확정 대기.',
  },
  {
    name: 'SHIPPED',
    label: '출고 완료',
    terminal: true,
    desc: '출고 확정 · 화물(Shipment) 생성. 취소 불가(반품/RMA는 v2). 택배/출고 표에 이때 나타난다.',
  },
  {
    name: 'CANCELLED',
    label: '취소',
    terminal: true,
    desc: '출고 전(접수~패킹완료) 취소. 재고 예약은 보상 흐름으로 해제. OUTBOUND_ADMIN 롤 필요.',
  },
  {
    name: 'BACKORDERED',
    label: '재고부족 이월',
    terminal: true,
    desc: '재고 부족(INSUFFICIENT_STOCK)으로 예약 실패 → 진행 중단. REST가 아니라 재고 이벤트로만 진입.',
  },
];

/** 출고 확정 후 화물의 TMS(운송사) 통보 상태. */
export interface TmsState {
  name: string;
  label: string;
  desc: string;
}

export const TMS_STATES: TmsState[] = [
  {
    name: 'PENDING',
    label: '통보 대기',
    desc: '화물 생성됨 · TMS 통보 미시도(또는 진행 중). 데모엔 TMS mock이 없어 여기 머물며 콘솔에 "수동 TMS 재시도"가 노출된다.',
  },
  {
    name: 'NOTIFIED',
    label: '통보 완료',
    desc: 'TMS가 화물 통보를 수신·확인. 운송장번호가 채워진다.',
  },
  {
    name: 'NOTIFY_FAILED',
    label: '통보 실패',
    desc: 'TMS 푸시가 재시도/서킷/벌크헤드를 소진. 수동 재시도 대상.',
  },
];

/**
 * saga(OutboundSaga)는 주문 상태머신과 lock-step으로 병렬 진행하되 별도 추적
 * 되는 조율 상태머신이다. 운영자가 직접 다루지 않지만 알림·문제 상태의 출처.
 */
export const SAGA_NOTE = {
  title: '참고: 출고 사가(Saga)',
  body: '주문/화물 상태와 별개로, 내부 조율용 사가가 REQUESTED → RESERVED → PICKING_CONFIRMED → PACKING_CONFIRMED → SHIPPED → COMPLETED 로 병렬 진행한다. 예외 상태로 RESERVE_FAILED(재고부족), CANCELLATION_REQUESTED(취소 대기), SHIPPED_NOT_NOTIFIED(출고됐으나 TMS 통보 미완 — 위 데모가 여기), STUCK_RECOVERY_FAILED(재시도 소진)가 있다. 운영자에겐 알림·"점검 필요" 신호로 드러난다.',
} as const;

// ───────────────────────── 도메인 롤 ─────────────────────────

/**
 * WMS 도메인 롤 — 운영자가 wms 구독 테넌트로 assume-tenant 할 때 파생되어
 * (auth-service `OperatorRoleDerivation`) 재고/출고 서비스를 게이트한다. IAM
 * 가이드의 admin-console 역할과는 다른 축(도메인 롤).
 */
export interface WmsRole {
  role: string;
  surface: string;
  desc: string;
}

export const WMS_ROLES: WmsRole[] = [
  {
    role: 'INVENTORY_READ',
    surface: '재고 조회',
    desc: '재고 현황 조회 엔드포인트.',
  },
  {
    role: 'INVENTORY_WRITE',
    surface: '재고 조정 · 이동',
    desc: '조정 / 손상표시 / 위치 이동.',
  },
  {
    role: 'INVENTORY_ADMIN',
    surface: '재고 고급',
    desc: '손상 폐기, 예약 버킷 조정, 수동 예약 해제.',
  },
  {
    role: 'OUTBOUND_READ',
    surface: '출고 조회',
    desc: '출고 주문·택배/출고 조회. assume-tenant 시 주입된다.',
  },
  {
    role: 'OUTBOUND_WRITE',
    surface: '출고 처리',
    desc: '피킹/패킹/출고 확정.',
  },
  {
    role: 'OUTBOUND_ADMIN',
    surface: '출고 취소',
    desc: '출고 전 주문 취소.',
  },
];

// ───────────────────────── 작업 레시피 (TASK-PC-FE-256) ─────────────────────────

/**
 * WMS 작업 레시피 — 재고(가용 버킷·조정) · 출고(주문 상태머신·취소 롤) · TMS
 * 통보 상태라는 이 화면의 실제 상태·화면만 참조한다.
 */
export const WMS_RECIPES: GuideRecipeData[] = [
  {
    title: '재고가 모자라 출고가 이월(BACKORDERED)됐을 때',
    steps: [
      '재고 화면(/wms/inventory)에서 해당 SKU 의 가용 버킷을 확인합니다 — 가용이 부족하면 예약이 실패해 출고가 이월됩니다.',
      '입고 적치나 재고 조정으로 가용 수량을 채웁니다(재고 변동 = 입고/조정).',
      '이미 BACKORDERED(종료 상태)로 빠진 주문은 되살아나지 않으니, 재고를 채운 뒤 새 출고를 생성해 정상 경로(접수→피킹→…)로 태웁니다.',
    ],
  },
  {
    title: '출고를 취소해야 할 때',
    steps: [
      '출고 화면(/wms/outbound)에서 주문 상태를 확인합니다 — 취소는 출고완료(SHIPPED) 전(접수~패킹완료)에만 가능합니다.',
      '취소하면 잡혀 있던 재고 예약이 보상 흐름으로 해제되어 가용으로 복귀합니다(예약 RESERVED→RELEASED).',
      '출고 취소에는 OUTBOUND_ADMIN 롤이 필요합니다.',
    ],
  },
  {
    title: '택배가 운송사에 통보되지 않을 때',
    steps: [
      '택배/출고 표에서 TMS 통보 상태가 통보 대기(PENDING) 또는 통보 실패(NOTIFY_FAILED)인지 확인합니다.',
      '"수동 TMS 재시도"로 다시 통보를 시도합니다 — 통보가 완료(NOTIFIED)되면 운송장번호가 채워집니다.',
      '데모 환경에는 TMS mock 이 없어 통보 대기에 머무는 것이 정상입니다(장애 아님).',
    ],
  },
];

// ───────────────────────── 용어집 (TASK-PC-FE-256) ─────────────────────────

/**
 * WMS 용어집 — 화면에 실제 렌더되는 문자열 중 일반 운영자가 모를 법한 용어만.
 * 버킷·예약 흐름은 화면이 이미 표로 설명하므로 제외.
 */
export const WMS_GLOSSARY: GlossaryEntry[] = [
  {
    key: 'SKU',
    term: 'SKU',
    full: 'Stock Keeping Unit',
    meaning:
      '재고를 관리하는 최소 상품 단위. 위치·로트와 함께 재고 수량을 식별하는 키가 됩니다.',
  },
  {
    key: 'TMS',
    term: '운송사 통보 (TMS)',
    full: 'Transportation Management System',
    meaning:
      '출고 확정된 화물을 택배사에 넘기는 운송 관리 시스템. 통보 대기·완료·실패 상태로 택배/출고 표에 표시됩니다.',
  },
  {
    key: 'saga',
    term: '사가 (saga)',
    meaning:
      '출고의 여러 단계를 뒤에서 조율하는 내부 상태머신. 운영자가 직접 다루지 않지만 알림·"점검 필요" 신호의 출처입니다.',
  },
  {
    key: 'assume-tenant',
    term: '테넌트 선택 (assume-tenant)',
    meaning:
      '운영자가 특정 테넌트를 골라 그 테넌트의 도메인 권한을 부여받는 동작. 이때 WMS 도메인 롤(재고·출고 읽기/쓰기)이 자동으로 파생됩니다.',
  },
];
