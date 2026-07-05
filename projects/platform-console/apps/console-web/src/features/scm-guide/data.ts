/**
 * SCM 가이드 화면의 정적 참조 데이터 (TASK-PC-FE-188).
 *
 * SCM 콘솔의 3개 라이브 화면 — **개요(`/scm`: 발주 + 재고 가시성)** · **보충
 * (`/scm/replenishment`)** · **설정(`/scm/config`)** — 이 실제로 보여주는 값의
 * 의미와, 그 뒤의 scm-platform 마이크로서비스 구성을 운영자에게 설명한다. IAM
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
 * 시스템이다. 콘솔은 gateway 를 경유해 procurement(발주)·inventory-visibility
 * (재고 가시성)·demand-planning(보충·설정) 의 운영자 API 를 호출해 화면을
 * 렌더한다. SCM 은 v1 **단일테넌트** 도메인이다(맨 아래 롤 참조).
 */
export const DOMAIN_SERVICES: DomainService[] = [
  {
    key: 'gateway',
    name: 'gateway-service',
    context: '엣지 · 인증/라우팅',
    desc: 'scm 단일 엣지 게이트웨이(Spring Cloud Gateway). JWT `tenant_id ∈ {scm, *}` 검증(단일테넌트 fail-closed) + entitlement 이중수용 후, 아래 3개 producer 로 라우팅.',
    console: '— (전 화면 경유)',
  },
  {
    key: 'procurement',
    name: 'procurement-service',
    context: '발주 · 구매',
    desc: '발주(PurchaseOrder) 애그리거트를 소유. DRAFT→제출→확정→입고→정산 생명주기, 공급사 확인(ack)·부분입고·정산을 관리.',
    console: '개요 (발주)',
  },
  {
    key: 'inventory-visibility',
    name: 'inventory-visibility-service',
    context: '재고 가시성',
    desc: '다중 노드(창고/매장) 재고를 이벤트로 투영한 교차 조회 읽기모델. 모든 응답에 S5 경고("발주 결정 근거 아님")를 상시 부착.',
    console: '개요 (재고 스냅샷)',
  },
  {
    key: 'demand-planning',
    name: 'demand-planning-service',
    context: '수요계획 · 보충',
    desc: 'wms 저재고 알림을 소비해 보충 추천(SUGGESTED)을 생성, 운영자 승인 시 DRAFT 발주로 물질화(ADR-MONO-027 루프). 재주문 정책·공급사 매핑 시드를 소유.',
    console: '보충 · 설정',
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
 * **핵심 구분**: 콘솔 SCM 개요의 발주 목록은 **읽기 전용**이다(제출·확정·입고
 * 등 쓰기는 조달 백엔드 책임). 콘솔에서 발주 생성을 촉발하는 유일한 경로는
 * 보충 추천 **승인** 뿐이며, 그것도 **DRAFT** 까지만 만든다(PO_NOTE 참조).
 */
export const PO_STATES: PoState[] = [
  {
    name: 'DRAFT',
    label: '초안',
    terminal: false,
    desc: '발주 초안(제출 전, 수정 가능). 보충 추천 승인이 만드는 PO 가 이 상태로 태어난다.',
  },
  {
    name: 'SUBMITTED',
    label: '제출',
    terminal: false,
    desc: '공급사에 제출됨. 공급사 확인(ack) 대기.',
  },
  {
    name: 'ACKNOWLEDGED',
    label: '접수',
    terminal: false,
    desc: '공급사가 발주를 접수(ack).',
  },
  {
    name: 'CONFIRMED',
    label: '확정',
    terminal: false,
    desc: '공급사가 납기·수량을 확정, 입고 대기. 확정(confirm) 액션은 roles∋OPERATOR 를 요구한다.',
  },
  {
    name: 'PARTIALLY_RECEIVED',
    label: '부분입고',
    terminal: false,
    desc: '발주 라인의 일부 수량만 입고됨.',
  },
  {
    name: 'RECEIVED',
    label: '입고',
    terminal: false,
    desc: '전 라인 입고 완료.',
  },
  {
    name: 'SETTLED',
    label: '정산',
    terminal: false,
    desc: '입고분 정산 완료.',
  },
  {
    name: 'CLOSED',
    label: '마감',
    terminal: true,
    desc: '발주 종료(비활성). 종료 상태.',
  },
  {
    name: 'CANCELED',
    label: '취소',
    terminal: true,
    desc: '발주 취소. 종료 상태.',
  },
];

/**
 * 콘솔 발주가 읽기 전용이라는 점 + 보충 승인만이 DRAFT 를 만든다는 점을 명시.
 */
export const PO_NOTE = {
  title: '콘솔 발주는 읽기 전용 · 보충 승인이 DRAFT 를 만든다',
  body: '콘솔 SCM 개요의 발주 목록은 조회 전용이다 — 제출(SUBMIT)·확정(CONFIRM)·입고(RECEIVE) 등 쓰기는 조달(procurement) 백엔드의 책임이다. 콘솔에서 발주 생성을 촉발하는 유일한 경로는 보충 추천 **승인**이며, 승인 시 procurement 는 **DRAFT** 발주만 만들고(그 이상 자동 진행 없음, ADR-MONO-027 D5), 이후 제출·확정(확정은 roles∋OPERATOR 필요)은 조달에서 별도로 진행한다.',
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
    desc: '노드 스냅샷이 최근 이벤트로 갱신됨. 정상.',
  },
  {
    name: 'STALE',
    label: '지연',
    desc: '스냅샷이 오래됨(이벤트 투영 지연). 주의 — 값이 최신이 아닐 수 있다.',
  },
  {
    name: 'UNREACHABLE',
    label: '도달불가',
    desc: '노드 프로브 실패. 해당 노드 재고를 신뢰할 수 없음.',
  },
];

/**
 * S5 경고 — inventory-visibility 계약의 NORMATIVE 의무. 콘솔은 절대 숨기지
 * 않고 재고 스냅샷이 보일 때 상단에 노출한다(`features/scm-ops` S5Warning).
 */
export const S5_NOTE = {
  title: '재고 가시성 S5 — 발주 결정 근거가 아니다',
  body: 'inventory-visibility 응답에는 항상 `Not for procurement decisions (S5)` 경고가 붙는다(계약 의무 — 콘솔은 이 문자열을 숨기거나 지우지 않고 재고 스냅샷 상단에 노출한다). 이 스냅샷은 다중 노드(창고/매장) 재고를 이벤트로 투영한 최종 일관성 읽기모델이라 순간적으로 과거일 수 있고, 실제 발주/재고 차감의 권위는 wms inventory-service 다. 개요의 재고 스냅샷 카운트가 보일 때 이 경고가 함께 뜬다.',
} as const;

/**
 * 노드(Node) 개념 + 교차 조회 안내.
 */
export const NODE_NOTE = {
  title: '노드(Node)와 교차 조회',
  body: '노드는 재고를 보유하는 물리 위치(창고·매장 등, nodeType)다. inventory-visibility 는 SKU 별로 여러 노드의 수량을 합산해 교차 조회를 제공한다(개요의 재고 스냅샷·SKU 분해·노드 목록). 노드별 staleness 로 어느 노드 데이터가 지연/도달불가인지 구분하며, 지연/도달불가 노드가 있으면 합산 수량을 그만큼 낮은 신뢰로 읽어야 한다.',
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
    desc: 'wms 저재고 알림으로 생성된 미결 추천(source=ALERT). 운영자 승인 또는 기각 대기. 트리거 가용재고가 추천 사유로 기록된다.',
  },
  {
    name: 'APPROVED',
    label: '승인',
    terminal: false,
    operatorActionable: true,
    desc: '운영자가 승인, DRAFT 발주 물질화 진행 중(과도기 상태).',
  },
  {
    name: 'MATERIALIZED',
    label: '물질화',
    terminal: true,
    operatorActionable: false,
    desc: '승인이 DRAFT 발주를 만든 정상 종료. materializedPoId 로 발주와 연결. 재승인해도 같은 PO 를 반환(멱등).',
  },
  {
    name: 'DISMISSED',
    label: '기각',
    terminal: true,
    operatorActionable: false,
    desc: '운영자가 기각한 종료. 미결 추천 가드가 해제된다. 재기각해도 변화 없음(멱등).',
  },
];

/**
 * ADR-MONO-027 보충 루프 — wms 저재고 알림 → 추천 → DRAFT 발주. 콘솔 보충
 * 화면이 서 있는 전체 흐름을 설명한다.
 */
export const REPLENISHMENT_LOOP_NOTE = {
  title: '보충 루프 (ADR-MONO-027): 저재고 알림 → 추천 → DRAFT 발주',
  body: '① wms inventory-service 가 가용재고 < 임계에 도달하면 저재고 알림(`wms.inventory.alert.v1`)을 발행한다. ② demand-planning 이 이를 소비해 해당 SKU 의 재주문 정책(reorderPoint)과 비교하고, 미달이면 보충 추천(SUGGESTED · 추천수량=reorderQty · source=ALERT · 트리거 가용재고 기록)을 만든다. ③ 운영자가 콘솔 보충 화면에서 승인하면 공급사 매핑(sku_supplier_map)을 해석해 procurement 가 DRAFT 발주를 생성하고 추천은 MATERIALIZED 로 종료된다. ④ 그 DRAFT 발주의 제출·확정은 조달에서 별도로 진행한다. 공급사 매핑이 없으면 승인이 422(SKU_SUPPLIER_UNMAPPED)로 막히고 추천은 SUGGESTED 로 남으므로, 설정 화면에서 매핑을 먼저 등록해야 한다.',
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
    desc: '가용재고가 이 값 아래로 떨어지면 보충 추천 대상이 된다(루프 ②의 임계). 비음수 정수.',
  },
  {
    key: 'safetyStock',
    field: 'safetyStock',
    label: '안전재고',
    desc: '수요 변동 대비 완충 재고. 비음수 정수.',
  },
  {
    key: 'reorderQty',
    field: 'reorderQty',
    label: '발주수량',
    desc: '추천/발주 시 채우는 수량(추천수량의 근거값). 양의 정수.',
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
    desc: '발주 대상 공급사 식별자. v1 은 자유텍스트/uuid — 공급사 마스터가 없는 최소 대체(ADR-MONO-027 D3), 실존 공급사 조회 없음.',
  },
  {
    key: 'defaultOrderQty',
    field: 'defaultOrderQty',
    label: '기본발주수량',
    desc: '이 매핑의 기본 발주 수량. 양의 정수.',
  },
  {
    key: 'leadTimeDays',
    field: 'leadTimeDays',
    label: '리드타임(일)',
    desc: '발주~입고 예상 소요일. 비음수 정수.',
  },
  {
    key: 'currency',
    field: 'currency',
    label: '통화',
    desc: '3-letter ISO-4217 코드(KRW·USD·… ). 대문자 입력.',
  },
];

/**
 * 설정 화면의 SKU-단위 upsert · 404=미설정 빈 상태 안내.
 */
export const CONFIG_NOTE = {
  title: '설정: 재주문 정책 · 공급사 매핑 (SKU 단위 upsert)',
  body: '설정 화면은 SKU 코드 단위로 재주문 정책과 공급사 매핑을 조회(GET)·저장(PUT upsert)한다. 목록 라우트가 없어(producer 가 per-SKU GET/PUT 만 제공) 운영자가 SKU 코드를 입력하면 두 행을 함께 GET 한다. 미설정 SKU 의 GET 404(POLICY_NOT_FOUND / MAPPING_NOT_FOUND)는 오류가 아니라 "아직 미설정 → PUT 으로 생성" 빈 상태다. 이 두 설정이 보충 루프의 ②(정책)과 ③(공급사 매핑)을 구동한다.',
} as const;

// ───────────────────────── 도메인 롤 · 단일테넌트 ─────────────────────────

/**
 * SCM 도메인 롤(단일 SCM_OPERATOR) + 단일테넌트 안내. 운영자가 scm 구독 테넌트로
 * assume-tenant 할 때 auth-service `OperatorRoleDerivation` 이 파생한다. WMS(세분
 * 롤 다수)와 달리 단일 coarse 롤 하나뿐 — E-Commerce 의 단일 ADMIN 과 유사.
 */
export const SCM_ROLE_NOTE = {
  title: 'SCM 도메인 롤 (단일 SCM_OPERATOR) · 단일테넌트',
  body: 'SCM 은 v1 단일테넌트 도메인이다 — scm-gateway 는 JWT `tenant_id ∈ {scm, *}` 만 허용하고(다른 테넌트는 403 TENANT_FORBIDDEN), 운영자는 콘솔 테넌트 스위처에서 `scm` 을 선택(assume-tenant)해야 도메인 토큰이 tenant_id=scm 으로 발급된다. 이때 auth-service OperatorRoleDerivation 이 단일 도메인 롤 `SCM_OPERATOR` 를 파생한다(WMS 의 화면별 세분 롤과 달리 하나뿐, E-Commerce 의 단일 ADMIN 과 유사). demand-planning(보충·설정)은 테넌트 게이트만으로 열리고 별도 롤 체크가 없으나, 조달의 발주 확정(confirm)은 roles∋OPERATOR 를 요구한다. (콘솔 자체를 게이트하는 admin-console 역할과는 다른 축 — IAM 가이드 참조.)',
} as const;
