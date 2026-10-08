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
    desc: '지금 바로 출고(피킹)에 쓸 수 있는 수량입니다. 예약이 잡히면 이 수량이 줄고 예약으로 옮겨갑니다.',
  },
  {
    key: 'reserved',
    label: '예약',
    field: 'reserved_qty',
    pickable: false,
    desc: '출고를 위해 이미 잡아 둔 수량입니다. 출고가 끝나면 사라지고, 취소되거나 시간이 지나면 다시 가용으로 돌아갑니다.',
  },
  {
    key: 'damaged',
    label: '손상',
    field: 'damaged_qty',
    pickable: false,
    desc: '팔 수 없는 재고입니다. 창고에 실제로 있어서 보유 수량에는 들어가지만, 출고에는 쓸 수 없습니다.',
  },
  {
    key: 'onHand',
    label: '보유',
    field: 'on_hand (파생)',
    pickable: false,
    desc: '창고에 실제로 있는 전체 수량입니다 = 가용 + 예약 + 손상. 따로 저장하지 않고 세 수량을 더해 계산합니다.',
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
    trigger: '출고에서 피킹을 요청하면 재고가 예약됩니다.',
    effect: '가용 수량이 줄고 예약 수량이 늘어납니다. 재고가 부족하면 예약에 실패해 출고가 재고부족 이월(BACKORDERED) 상태가 됩니다.',
  },
  {
    step: '확정 (CONFIRMED)',
    trigger: '출고가 확정되면 예약이 끝납니다.',
    effect: '예약 수량만 줄어듭니다(가용은 예약될 때 이미 줄었으므로 그대로입니다). 더는 바뀌지 않는 상태입니다.',
  },
  {
    step: '해제 (RELEASED)',
    trigger: '출고가 취소되거나, 일정 시간(기본 24시간)이 지나 자동 만료되거나, 운영자가 직접 해제할 때 일어납니다.',
    effect: '예약 수량이 줄고 가용 수량이 다시 늘어납니다. 더는 바뀌지 않는 상태입니다.',
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
    desc: '입고 처리가 끝나면 가용 수량이 늘어납니다.',
  },
  {
    event: 'inventory.adjusted',
    label: '조정',
    desc: '실사·분실·발견·손상 처리 등 사유를 남기고 운영자가 직접 수량을 고치는 것입니다.',
  },
  {
    event: 'inventory.transferred',
    label: '이동',
    desc: '같은 창고 안에서 한 위치의 재고를 다른 위치로 옮기는 것입니다.',
  },
  {
    event: 'inventory.reserved',
    label: '예약',
    desc: '출고를 위해 재고를 잡아 두는 것입니다(가용→예약).',
  },
  {
    event: 'inventory.released',
    label: '해제',
    desc: '취소·만료·수동 해제로 예약을 다시 풀어 주는 것입니다(예약→가용).',
  },
  {
    event: 'inventory.confirmed',
    label: '확정',
    desc: '출고가 끝나 예약해 둔 재고를 실제로 사용 처리하는 것입니다.',
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
    threshold: '고정값 · 가용 10개 이하',
    desc: '가용 수량이 10개 이하면 자동으로 표시되는 고정 기준입니다. 상품(SKU)마다 다르게 정할 수는 없습니다.',
  },
  {
    where: '운영자 저재고 알림',
    threshold: '설정 가능 · 창고·상품(SKU)별로 정함, 없으면 전체 기본값',
    desc: '가용 수량이 미리 정한 기준보다 적어지면 운영자에게 알림이 갑니다. 기준을 정하지 않았으면 알림이 가지 않습니다.',
  },
];

/**
 * 읽기모델 최종 일관성 안내. 콘솔 재고/출고 표는 이벤트로 투영된 **읽기모델**
 * (admin-service `admin_inventory_snapshot` / `admin_shipment_summary`)이라
 * 쓰기 시스템 대비 잠시 과거일 수 있다.
 */
export const READ_MODEL_NOTE = {
  title: '표시값이 살짝 늦을 수 있어요',
  body: '재고·출고 표의 값은 실시간이 아니라 약간의 시간 차를 두고 반영됩니다. 반영이 5초 이상 늦어지면 화면 위에 "표시값이 잠시 과거일 수 있습니다"라는 안내가 뜹니다. 값은 정상이며 금방 최신 값으로 맞춰집니다.',
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
    desc: '주문이 접수된 상태입니다. 접수되면 곧바로 피킹중으로 넘어가서, 이 상태에 오래 머무는 것을 보기는 어렵습니다.',
  },
  {
    name: 'PICKING',
    label: '피킹 중',
    terminal: false,
    desc: '재고를 피킹(꺼내기) 중이며, 그만큼 재고가 예약된 상태입니다. 이때부터 주문 내용은 바뀌지 않지만 취소는 할 수 있습니다.',
  },
  {
    name: 'PICKED',
    label: '피킹 완료',
    terminal: false,
    desc: '모든 상품의 피킹이 끝나 포장(패킹)을 기다리는 상태입니다.',
  },
  {
    name: 'PACKING',
    label: '패킹 중',
    terminal: false,
    desc: '포장을 진행하고 있는 상태입니다(아직 다 끝나지 않았습니다).',
  },
  {
    name: 'PACKED',
    label: '패킹 완료',
    terminal: false,
    desc: '포장이 모두 끝나 출고 확정을 기다리는 상태입니다.',
  },
  {
    name: 'SHIPPED',
    label: '출고 완료',
    terminal: true,
    desc: '출고가 확정되어 택배(화물)가 만들어진 상태입니다. 이 뒤로는 취소할 수 없고, 택배/출고 표에 나타납니다.',
  },
  {
    name: 'CANCELLED',
    label: '취소',
    terminal: true,
    desc: '출고되기 전(접수~패킹완료 사이)에 취소된 상태입니다. 잡혀 있던 재고 예약은 풀립니다. 취소에는 OUTBOUND_ADMIN 롤이 필요합니다.',
  },
  {
    name: 'BACKORDERED',
    label: '재고부족 이월',
    terminal: true,
    desc: '재고가 부족해 예약에 실패하면서 더 진행되지 못한 상태입니다. 화면에서 직접 만들 수 없고, 재고 부족이 감지될 때만 자동으로 생깁니다.',
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
    desc: '택배가 만들어졌지만 아직 운송사에 통보하지 않았거나 통보 중인 상태입니다. 데모 환경에서는 운송사 연결이 없어 이 상태로 남는 것이 정상입니다 — 이때 "수동 TMS 재시도" 버튼이 나타납니다.',
  },
  {
    name: 'NOTIFIED',
    label: '통보 완료',
    desc: '운송사가 통보를 받아 확인한 상태입니다. 운송장번호가 채워집니다.',
  },
  {
    name: 'NOTIFY_FAILED',
    label: '통보 실패',
    desc: '운송사에 여러 번 통보를 시도했지만 실패한 상태입니다. 수동 재시도가 필요합니다.',
  },
];

/**
 * saga(OutboundSaga)는 주문 상태머신과 lock-step으로 병렬 진행하되 별도 추적
 * 되는 조율 상태머신이다. 운영자가 직접 다루지 않지만 알림·문제 상태의 출처.
 */
export const SAGA_NOTE = {
  title: '참고: 출고 사가(saga)',
  body: '주문·택배 상태와 별도로, 내부 시스템이 출고 과정 전체를 뒤에서 조율합니다. 운영자가 직접 다루는 화면은 아니지만, 재고 부족이나 운송사 통보 실패 같은 문제가 생기면 알림이나 "점검 필요" 표시로 드러납니다.',
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
    desc: '재고 현황을 조회할 수 있습니다.',
  },
  {
    role: 'INVENTORY_WRITE',
    surface: '재고 조정 · 이동',
    desc: '재고를 조정하거나 손상 표시, 위치 이동을 할 수 있습니다.',
  },
  {
    role: 'INVENTORY_ADMIN',
    surface: '재고 고급',
    desc: '손상 재고 폐기, 예약 수량 조정, 수동 예약 해제를 할 수 있습니다.',
  },
  {
    role: 'OUTBOUND_READ',
    surface: '출고 조회',
    desc: '출고 주문과 택배/출고 목록을 조회할 수 있습니다.',
  },
  {
    role: 'OUTBOUND_WRITE',
    surface: '출고 처리',
    desc: '피킹, 패킹, 출고 확정을 처리할 수 있습니다.',
  },
  {
    role: 'OUTBOUND_ADMIN',
    surface: '출고 취소',
    desc: '출고되기 전인 주문을 취소할 수 있습니다.',
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
      '재고 화면에서 해당 상품(SKU)의 가용 수량을 확인하세요 — 가용 수량이 부족하면 예약이 실패해 출고가 이월(BACKORDERED)됩니다.',
      '입고를 받거나 재고를 조정해서 가용 수량을 채웁니다.',
      '이미 이월된 주문은 다시 진행되지 않으니, 재고를 채운 뒤 새 출고를 만들어 처음부터 다시 진행하세요.',
    ],
  },
  {
    title: '출고를 취소해야 할 때',
    steps: [
      '출고 화면에서 주문 상태를 확인하세요 — 출고완료 전(접수~패킹완료 사이)에만 취소할 수 있습니다.',
      '취소하면 잡혀 있던 재고 예약이 풀려 다시 가용 수량으로 돌아갑니다.',
      '출고 취소에는 OUTBOUND_ADMIN 롤이 필요합니다.',
    ],
  },
  {
    title: '택배가 운송사에 통보되지 않을 때',
    steps: [
      '택배/출고 표에서 통보 상태가 통보 대기 또는 통보 실패인지 확인하세요.',
      '"수동 TMS 재시도" 버튼으로 다시 통보해 보세요 — 통보가 완료되면 운송장번호가 채워집니다.',
      '데모 환경에서는 운송사 쪽 연결이 없어 통보 대기 상태로 남는 것이 정상입니다 — 문제가 아닙니다.',
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
      '재고를 관리하는 최소 상품 단위입니다. 위치·로트와 함께 재고 수량을 구분하는 기준이 됩니다.',
  },
  {
    key: 'TMS',
    term: '운송사 통보 (TMS)',
    full: 'Transportation Management System',
    meaning:
      '출고된 택배를 운송사(택배사)에 전달하는 시스템입니다. 통보 대기·완료·실패 상태로 택배/출고 표에 표시됩니다.',
  },
  {
    key: 'saga',
    term: '사가 (saga)',
    meaning:
      '출고 처리 과정을 뒤에서 조율하는 내부 장치입니다. 운영자가 직접 다루지 않지만, 문제가 생기면 알림이나 "점검 필요" 표시로 드러납니다.',
  },
  {
    key: 'assume-tenant',
    term: '테넌트 선택 (assume-tenant)',
    meaning:
      '운영자가 특정 회사(테넌트)를 선택해 그 회사에서 쓸 권한을 받는 동작입니다. 이때 WMS 재고·출고 권한이 자동으로 주어집니다.',
  },
];
