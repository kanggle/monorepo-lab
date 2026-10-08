/**
 * SCM 가이드 화면의 정적 참조 데이터 (TASK-PC-FE-188).
 *
 * SCM 콘솔의 라이브 화면 — **개요(`/scm`: 운영 현황 요약)** · **조달
 * (`/scm/procurement`: 발주)** · **재고(`/scm/inventory`: 재고 가시성)** ·
 * **보충 계획(`/scm/replenishment`)** · **보충 계획 설정(`/scm/config`)** — 이
 * 실제로 보여주는 값의 의미와, 그 뒤의 scm-platform 마이크로서비스 구성을
 * 운영자에게 설명한다(개요/조달/재고 분리는 TASK-PC-FE-220). IAM
 * 가이드(`features/iam-guide/data.ts`, TASK-PC-FE-163) · WMS 가이드
 * (`features/wms-guide/data.ts`, TASK-PC-FE-183) · E-Commerce 가이드
 * (`features/ecommerce-guide/data.ts`, TASK-PC-FE-184)와 같은 원칙: 타입 있는
 * 정적 배열 + 정적 화면. 데이터 페치·권한 게이트 없음(콘솔 진입자 누구나 열람).
 *
 * **SoT** (드리프트 시 이 파일 카피도 동반 갱신):
 *   - 발주(PO) 생명주기: `scm-platform/specs/contracts/http/procurement-api.md`
 *     + `apps/procurement-service` 도메인 모델(`PoStatus`).
 *   - 재고 가시성(S5·staleness·노드): `inventory-visibility-api.md`
 *     + `apps/inventory-visibility-service`.
 *   - 보충 추천·루프: `demand-planning-api.md` + `apps/demand-planning-service`
 *     (ADR-MONO-027).
 *   - 콘솔 소비 타입(producer enum verbatim 반영 — 2차 SoT):
 *     `features/scm-ops/api/types.ts` + `components/scm-ops-helpers.ts`
 *     (PO status/staleness) · `features/scm-replenishment/api/types.ts`
 *     (suggestion status/source) · `features/scm-config/api/types.ts`
 *     (policy/supplier-map 필드).
 *   - 도메인 롤: auth-service `OperatorRoleDerivation`(assume-tenant 파생 — scm
 *     → 단일 SCM_OPERATOR).
 *
 * 테스트(ScmGuideScreen.test.tsx)는 섹션/행 존재 등 **구조만** 단언하며, 설명
 * 텍스트 자체는 사람이 스펙과 맞춘다(iam-guide/wms-guide/ecommerce-guide data.ts
 * 동일 정책).
 */

import type {
  GlossaryEntry,
  GuideRecipeData,
} from '@/shared/ui/guide-primitives';

// ───────────────────────── 도메인 서비스 맵 ─────────────────────────

/** scm-platform 마이크로서비스 1개. */
export interface DomainService {
  key: string;
  /** 서비스명(참조용). */
  name: string;
  /** 바운디드 컨텍스트(한글). */
  context: string;
  /** 한 줄 책임. */
  desc: string;
  /** 이 서비스를 소비하는 콘솔 화면(없으면 '—'). */
  console: string;
}

/**
 * SCM 도메인은 단일 엣지 게이트웨이 뒤의 3개 producer 로 구성된 이벤트 기반
 * 시스템이다. 콘솔은 gateway 를 경유해 procurement(조달)·inventory-visibility
 * (재고)·demand-planning(보충 계획·보충 계획 설정) 의 운영자 API 를 호출해
 * 화면을 렌더한다. SCM 은 v1 **단일테넌트** 도메인이다(맨 아래 롤 참조).
 */
export const DOMAIN_SERVICES: DomainService[] = [
  {
    key: 'gateway',
    name: 'gateway-service',
    context: '입구 · 인증',
    desc: '로그인 정보를 확인하고, 요청을 맞는 서비스로 전달합니다.',
    console: '— (전 화면 경유)',
  },
  {
    key: 'procurement',
    name: 'procurement-service',
    context: '발주 · 구매',
    desc: '발주를 만들고 관리합니다. 제출부터 입고, 정산까지 전체 과정을 처리합니다.',
    console: '조달',
  },
  {
    key: 'inventory-visibility',
    name: 'inventory-visibility-service',
    context: '재고 가시성',
    desc: '여러 창고·매장의 재고를 한눈에 모아 보여줍니다. 발주 결정의 근거로는 쓸 수 없다는 경고가 항상 함께 표시됩니다.',
    console: '재고',
  },
  {
    key: 'demand-planning',
    name: 'demand-planning-service',
    context: '수요계획 · 보충',
    desc: '창고의 재고 부족 알림을 받아 보충 추천을 만듭니다. 운영자가 승인하면 발주 초안이 만들어집니다. 재주문 기준과 공급사 정보도 관리합니다.',
    console: '보충 계획 · 보충 계획 설정',
  },
];

// ───────────────────────── 발주 (Procurement PO) ─────────────────────────

/** 발주(PurchaseOrder) 상태. */
export interface PoState {
  name: string;
  label: string;
  terminal: boolean;
  desc: string;
}

/**
 * 발주 상태머신(`PoStatus`, 9). 정상 경로
 * `초안(DRAFT) → 제출(SUBMITTED) → 접수(ACKNOWLEDGED) → 확정(CONFIRMED) →
 * 부분입고(PARTIALLY_RECEIVED) → 입고(RECEIVED) → 정산(SETTLED)` + 종료 2개
 * (마감 CLOSED · 취소 CANCELED). 콘솔 소비 순서는
 * `scm-ops-helpers.KNOWN_PO_STATUSES` 와 일치한다.
 *
 * **핵심 구분**: 콘솔 SCM 조달 화면의 발주 목록은 **읽기 전용**이다(제출·확정·
 * 입고 등 쓰기는 조달 백엔드 책임). 콘솔에서 발주 생성을 촉발하는 유일한 경로는
 * 보충 추천 **승인** 뿐이며, 그것도 **DRAFT** 까지만 만든다(PO_NOTE 참조).
 */
export const PO_STATES: PoState[] = [
  {
    name: 'DRAFT',
    label: '초안',
    terminal: false,
    desc: '아직 제출하지 않은 초안입니다. 수정할 수 있고, 보충 추천을 승인하면 이 상태로 발주가 만들어집니다.',
  },
  {
    name: 'SUBMITTED',
    label: '제출',
    terminal: false,
    desc: '공급사에 제출되어 확인을 기다리는 상태입니다.',
  },
  {
    name: 'ACKNOWLEDGED',
    label: '접수',
    terminal: false,
    desc: '공급사가 발주를 확인했습니다.',
  },
  {
    name: 'CONFIRMED',
    label: '확정',
    terminal: false,
    desc: '공급사가 납기와 수량을 확정해 입고를 기다리는 상태입니다. 확정 처리에는 운영자 권한이 필요합니다.',
  },
  {
    name: 'PARTIALLY_RECEIVED',
    label: '부분입고',
    terminal: false,
    desc: '주문한 수량 중 일부만 입고되었습니다.',
  },
  {
    name: 'RECEIVED',
    label: '입고',
    terminal: false,
    desc: '주문한 수량이 모두 입고되었습니다.',
  },
  {
    name: 'SETTLED',
    label: '정산',
    terminal: false,
    desc: '입고된 내용의 정산까지 끝났습니다.',
  },
  {
    name: 'CLOSED',
    label: '마감',
    terminal: true,
    desc: '발주가 종료되어 더 이상 진행되지 않습니다.',
  },
  {
    name: 'CANCELED',
    label: '취소',
    terminal: true,
    desc: '발주가 취소되었습니다.',
  },
];

/**
 * 콘솔 발주가 읽기 전용이라는 점 + 보충 승인만이 DRAFT 를 만든다는 점을 명시.
 */
export const PO_NOTE = {
  title: '콘솔의 발주 목록은 조회만 가능합니다',
  body: '조달 화면의 발주 목록은 조회 전용입니다 — 제출·확정·입고 같은 처리는 발주를 직접 다루는 쪽에서 이루어집니다. 콘솔에서 새 발주를 만드는 유일한 방법은 보충 추천을 승인하는 것이며, 이때는 초안(DRAFT) 상태로만 만들어집니다. 이후 제출과 확정(확정에는 운영자 권한 필요)은 조달 쪽에서 별도로 진행합니다.',
} as const;

// ───────────────────────── 재고 가시성 (Inventory Visibility) ─────────────

/** 노드 스냅샷 신선도(staleness) 상태. */
export interface StalenessState {
  name: string;
  label: string;
  desc: string;
}

/**
 * 재고 가시성 스냅샷의 노드별 신선도(`staleness`, 3):
 * `FRESH → STALE → UNREACHABLE`. 콘솔은 이 값을 tolerant free string 으로 받아
 * 알 수 없는 값은 generic 으로 렌더한다(`scm-ops-helpers.stalenessTone`).
 */
export const STALENESS_STATES: StalenessState[] = [
  {
    name: 'FRESH',
    label: '최신',
    desc: '최근 정보로 갱신되어 있습니다. 정상 상태입니다.',
  },
  {
    name: 'STALE',
    label: '지연',
    desc: '정보가 갱신되지 않아 오래되었습니다. 값이 최신이 아닐 수 있으니 주의하세요.',
  },
  {
    name: 'UNREACHABLE',
    label: '도달불가',
    desc: '해당 위치와 연결할 수 없습니다. 이 재고 수치는 믿을 수 없습니다.',
  },
];

/**
 * S5 경고 — inventory-visibility 계약의 NORMATIVE 의무. 콘솔은 절대 숨기지
 * 않고 재고 스냅샷이 보일 때 상단에 노출한다(`features/scm-ops` S5Warning).
 */
export const S5_NOTE = {
  title: 'S5 경고 — 발주 결정의 근거로 쓸 수 없습니다',
  body: '재고 화면에는 이 경고가 항상 함께 표시됩니다. 여러 창고·매장의 재고를 모아 보여주는 화면이라 실제와 약간의 시간 차이가 있을 수 있고, 정확한 재고와 발주 결정은 창고(WMS) 쪽 정보를 기준으로 해야 합니다.',
} as const;

/**
 * 노드(Node) 개념 + 교차 조회 안내.
 */
export const NODE_NOTE = {
  title: '노드(Node)란 무엇인가요',
  body: '노드는 창고나 매장처럼 재고를 보관하는 장소를 뜻합니다. 재고 화면은 상품(SKU)별로 여러 노드의 수량을 모두 더해 보여줍니다. 지연·도달불가 상태인 노드가 있으면 합산된 수량도 그만큼 덜 믿을 수 있습니다.',
} as const;

// ───────────────────────── 보충 추천 (Replenishment) ─────────────────────────

/** 보충 추천(Suggestion) 상태. */
export interface SuggestionState {
  name: string;
  label: string;
  terminal: boolean;
  /** 운영자가 이 상태에서 승인/기각할 수 있는가(콘솔 보충 화면 작업 버튼). */
  operatorActionable: boolean;
  desc: string;
}

/**
 * 보충 추천 상태머신(`status`, 4). `추천(SUGGESTED) → 승인(APPROVED) →
 * 물질화(MATERIALIZED)` 정상 경로 + 기각(DISMISSED) 종료. 콘솔 소비 순서는
 * `features/scm-replenishment/api/types.KNOWN_SUGGESTION_STATUSES` 와 일치한다.
 * 승인/기각은 SUGGESTED·APPROVED 에서만 가능(`canApprove`/`canDismiss`).
 */
export const SUGGESTION_STATES: SuggestionState[] = [
  {
    name: 'SUGGESTED',
    label: '추천',
    terminal: false,
    operatorActionable: true,
    desc: '창고의 재고 부족 알림으로 만들어진 추천입니다. 운영자의 승인이나 기각을 기다립니다.',
  },
  {
    name: 'APPROVED',
    label: '승인',
    terminal: false,
    operatorActionable: true,
    desc: '운영자가 승인해 발주로 만들어지는 중입니다.',
  },
  {
    name: 'MATERIALIZED',
    label: '물질화',
    terminal: true,
    operatorActionable: false,
    desc: '승인을 통해 발주가 만들어진 상태입니다. 다시 승인을 눌러도 같은 발주를 가리킵니다.',
  },
  {
    name: 'DISMISSED',
    label: '기각',
    terminal: true,
    operatorActionable: false,
    desc: '운영자가 기각한 상태입니다. 다시 기각을 눌러도 변화가 없습니다.',
  },
];

/**
 * ADR-MONO-027 보충 루프 — wms 저재고 알림 → 추천 → DRAFT 발주. 콘솔 보충
 * 화면이 서 있는 전체 흐름을 설명한다.
 */
export const REPLENISHMENT_LOOP_NOTE = {
  title: '보충 흐름: 재고 부족 알림 → 추천 → 발주 초안',
  body: '① 창고(WMS)의 재고가 기준보다 부족해지면 알림이 발생합니다. ② SCM 이 이 알림을 받아 해당 상품의 재주문 기준과 비교하고, 기준에 못 미치면 보충 추천(추천)을 만듭니다. ③ 운영자가 보충 화면에서 승인하면 등록된 공급사 정보를 바탕으로 발주 초안이 만들어지고, 추천은 완료(물질화) 상태로 바뀝니다. ④ 이후 발주의 제출과 확정은 조달 화면에서 따로 진행합니다. 해당 상품에 공급사 정보가 등록되어 있지 않으면 승인이 막히니, 설정 화면에서 공급사를 먼저 등록해야 합니다.',
} as const;

// ───────────────────────── 설정 (Config) ─────────────────────────

/** 설정 필드(재주문 정책 · 공급사 매핑) 1개. */
export interface ConfigField {
  key: string;
  field: string;
  label: string;
  desc: string;
}

/**
 * 재주문 정책(reorder policy) 필드 — SKU 단위. `reorderPoint` 미달이 보충
 * 추천을 만든다(루프 ②). SoT: `demand-planning-api.md` PUT /policies/{skuCode}
 * + `features/scm-config/api/types.ReorderPolicyInputSchema`.
 */
export const POLICY_FIELDS: ConfigField[] = [
  {
    key: 'reorderPoint',
    field: 'reorderPoint',
    label: '재주문점',
    desc: '재고가 이 수량 아래로 떨어지면 보충 추천 대상이 됩니다. 0 이상의 정수로 입력합니다.',
  },
  {
    key: 'safetyStock',
    field: 'safetyStock',
    label: '안전재고',
    desc: '수요 변화에 대비해 여유로 두는 재고입니다. 0 이상의 정수로 입력합니다.',
  },
  {
    key: 'reorderQty',
    field: 'reorderQty',
    label: '발주수량',
    desc: '추천이나 발주로 채우는 수량입니다. 1 이상의 정수로 입력합니다.',
  },
];

/**
 * 공급사 매핑(sku-supplier-map) 필드 — SKU 단위. 승인이 이 매핑을 해석해 DRAFT
 * PO 를 만든다(루프 ③). SoT: `demand-planning-api.md` PUT
 * /sku-supplier-map/{skuCode} + `features/scm-config/api/types.SupplierMapInputSchema`.
 */
export const SUPPLIER_FIELDS: ConfigField[] = [
  {
    key: 'supplierId',
    field: 'supplierId',
    label: '공급사',
    desc: '발주를 보낼 공급사를 나타내는 값입니다. 자유롭게 입력하며, 실제 존재하는 공급사인지는 따로 확인하지 않습니다.',
  },
  {
    key: 'defaultOrderQty',
    field: 'defaultOrderQty',
    label: '기본발주수량',
    desc: '이 공급사에 기본으로 발주할 수량입니다. 1 이상의 정수로 입력합니다.',
  },
  {
    key: 'leadTimeDays',
    field: 'leadTimeDays',
    label: '리드타임(일)',
    desc: '발주 후 입고까지 걸리는 예상 일수입니다. 0 이상의 정수로 입력합니다.',
  },
  {
    key: 'currency',
    field: 'currency',
    label: '통화',
    desc: '통화 코드입니다(KRW · USD 등). 대문자로 입력합니다.',
  },
];

/**
 * 설정 화면의 SKU-단위 upsert · 404=미설정 빈 상태 안내.
 */
export const CONFIG_NOTE = {
  title: '설정은 상품(SKU) 단위로 저장합니다',
  body: '설정 화면은 상품(SKU) 코드를 입력해 재주문 정책과 공급사 매핑을 함께 조회하고 저장합니다. 아직 설정하지 않은 상품은 빈 화면으로 보이는데, 오류가 아니라 처음 등록하면 되는 상태입니다. 이 두 설정이 보충 추천과 발주 초안 생성을 결정합니다.',
} as const;

// ───────────────────────── 도메인 롤 · 단일테넌트 ─────────────────────────

/**
 * SCM 도메인 롤(단일 SCM_OPERATOR) + 단일테넌트 안내. 운영자가 scm 구독 테넌트로
 * assume-tenant 할 때 auth-service `OperatorRoleDerivation` 이 파생한다. WMS(세분
 * 롤 다수)와 달리 단일 coarse 롤 하나뿐 — E-Commerce 의 단일 ADMIN 과 유사.
 */
export const SCM_ROLE_NOTE = {
  title: 'SCM 권한 안내',
  body: '콘솔에서 SCM 을 구독한 테넌트(예: 데모의 demo-corp)를 고르면 SCM 화면이 열리고, SCM 운영자(SCM_OPERATOR) 권한이 자동으로 주어집니다. WMS 처럼 화면별로 권한이 나뉘어 있지 않고 이 권한 하나뿐입니다. 보충 · 설정 화면은 테넌트만 맞으면 열리고, 조달 화면의 발주 확정만 운영자 역할을 따로 확인합니다.',
} as const;

// ───────────────────────── 작업 레시피 (TASK-PC-FE-256) ─────────────────────────

/**
 * SCM 작업 레시피 — 보충 루프(추천→승인→DRAFT 발주) · 설정(재주문 정책·공급사
 * 매핑) · 재고 가시성(신선도·S5)이라는 이 화면의 실제 상태·화면만 참조한다.
 */
export const SCM_RECIPES: GuideRecipeData[] = [
  {
    title: '보충 추천을 승인해 발주로 만들 때',
    steps: [
      '보충 화면에서 추천 상태인 항목을 엽니다 — 추천 · 승인 상태에서만 승인하거나 기각할 수 있습니다.',
      '승인하면 등록된 공급사 정보를 바탕으로 발주 초안이 만들어지고, 추천은 물질화 상태로 바뀝니다.',
      '공급사 정보가 등록되어 있지 않으면 승인할 수 없으니, 설정 화면에서 해당 상품의 공급사를 먼저 등록합니다.',
    ],
  },
  {
    title: '보충 추천이 자동으로 안 생길 때',
    steps: [
      '설정 화면에서 해당 상품의 재주문점이 설정되어 있는지 확인합니다.',
      '창고 재고가 재주문점 아래로 떨어져야 추천이 만들어집니다.',
      '공급사 정보도 함께 등록해야 나중에 승인했을 때 발주까지 이어집니다.',
    ],
  },
  {
    title: '재고 수치를 믿기 어려울 때',
    steps: [
      '재고 화면에서 노드별 신선도를 확인합니다 — 지연(STALE)·도달불가(UNREACHABLE) 상태인 노드가 있으면 합산 수량을 그만큼 덜 믿어야 합니다.',
      '이 화면에는 항상 경고가 함께 표시됩니다 — 발주 결정의 근거로는 쓰지 않습니다.',
      '정확한 재고와 발주 처리 기준은 창고(WMS) 쪽 정보입니다. 이 화면은 여러 곳을 모아 보여주는 터라 실제와 약간 차이가 있을 수 있습니다.',
    ],
  },
];

// ───────────────────────── 용어집 (TASK-PC-FE-256) ─────────────────────────

/**
 * SCM 용어집 — 화면에 실제 렌더되는 문자열 중 일반 운영자가 모를 법한 용어만.
 * 상태머신 enum(초안·제출…)은 표가 이미 한글 라벨과 설명을 붙였으므로 제외.
 */
export const SCM_GLOSSARY: GlossaryEntry[] = [
  {
    key: 'PO',
    term: '발주 (PO)',
    full: 'Purchase Order',
    meaning:
      '공급사에 물품을 주문하는 문서입니다. 초안부터 정산까지 여러 단계를 거치며, 콘솔에서는 조회만 할 수 있습니다.',
  },
  {
    key: 'S5',
    term: 'S5 경고',
    meaning:
      '재고 화면에 항상 함께 표시되는 경고로, 이 수치를 발주 결정의 근거로 쓰지 말라는 뜻입니다.',
  },
  {
    key: 'staleness',
    term: '신선도 (staleness)',
    meaning:
      '재고 정보가 얼마나 최신인지를 나타냅니다 — 최신(FRESH) · 지연(STALE) · 도달불가(UNREACHABLE). 지연이나 도달불가 상태는 믿기 어렵습니다.',
  },
  {
    key: 'SKU',
    term: 'SKU',
    full: 'Stock Keeping Unit',
    meaning:
      '재고와 발주를 관리하는 상품의 최소 단위입니다. 재주문 기준과 공급사 정보도 이 단위로 설정합니다.',
  },
];
