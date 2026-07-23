/**
 * ERP 가이드 화면의 정적 참조 데이터 (TASK-PC-FE-232).
 *
 * ERP 콘솔의 라이브 화면 — **개요(`/erp`: 마스터 5종 + 결재 대기 + 활성 위임
 * 건수 집계)** · **가이드(`/erp/guide`, 이 화면)** · **마스터
 * (`/erp/masters`: 부서·직원·직급·원가센터·거래처 조회/등록/수정/폐기)** ·
 * **통합 조회(`/erp/orgview`: read-model 기반 직원 조직도 투영)** · **결재함
 * (`/erp/approval`: 다단계 결재 요청/승인/반려/철회)** · **위임
 * (`/erp/delegation`: 결재 대결 grant 등록/철회)** — 가 실제로 보여주는 값의
 * 의미와, 그 뒤의 erp-platform 마이크로서비스 구성을 운영자에게 설명한다.
 * IAM 가이드(`features/iam-guide/data.ts`) · WMS 가이드
 * (`features/wms-guide/data.ts`) · SCM 가이드(`features/scm-guide/data.ts`) ·
 * Finance 가이드(`features/finance-guide/data.ts`)와 같은 원칙: 타입 있는
 * 정적 배열 + 정적 화면. 데이터 페치·권한 게이트 없음(콘솔 진입자 누구나
 * 열람).
 *
 * **SoT** (드리프트 시 이 파일 카피도 동반 갱신):
 *   - 마스터(부서·직원·직급·원가센터·거래처): `erp-platform/specs/contracts/http/masterdata-api.md`
 *     + `apps/masterdata-service` 도메인 모델.
 *   - 결재(다단계 워크플로·위임): `erp-platform/specs/contracts/http/approval-api.md`
 *     + `apps/approval-service` 도메인 모델.
 *   - 통합 조회(read-model 투영): `erp-platform/specs/contracts/http/read-model-api.md`
 *     + `apps/read-model-service` 도메인 모델.
 *   - 알림(벨 aggregator 통합): `erp-platform/specs/contracts/http/notification-api.md`.
 *   - 콘솔 소비 타입(producer enum verbatim 반영 — 2차 SoT):
 *     `features/erp-ops/api/types/**`(마스터·read-model) ·
 *     `features/erp-ops/api/approval-types.ts`(결재) ·
 *     `features/erp-ops/api/overview-state.ts`(개요 집계).
 *
 * 테스트(ErpGuideScreen.test.tsx)는 섹션/행 존재 등 **구조만** 단언하며,
 * 설명 텍스트 자체는 사람이 스펙과 맞춘다(iam/wms/scm/finance guide
 * data.ts 동일 정책).
 */

import type {
  GlossaryEntry,
  GuideRecipeData,
} from '@/shared/ui/guide-primitives';

// ───────────────────────── 도메인 서비스 맵 ─────────────────────────

/** erp-platform 마이크로서비스 1개. */
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
 * ERP 는 4개 producer 로 구성된다(v1, 별도 게이트웨이 없음 — 콘솔은 각
 * 서비스를 도메인-facing IAM OIDC 토큰으로 직접 호출한다, § 2.4.8).
 * `masterdata-service` 가 5종 마스터를 소유하고, `approval-service` 가
 * 그 위의 결재/위임 워크플로를, `read-model-service` 가 마스터의
 * eventually-consistent 투영(조직도)을, `notification-service` 가
 * 결재 전이 이벤트의 인앱 알림(벨)을 소유한다 — 후 3자는 모두 masterdata의
 * 다운스트림(v2-deferred `admin-service`/`permission-service` 는 v1
 * 범위 밖).
 */
export const DOMAIN_SERVICES: DomainService[] = [
  {
    key: 'masterdata-service',
    name: 'masterdata-service',
    context: '부서 · 직원 · 직급 · 원가센터 · 거래처',
    desc: '5종 마스터(Master) 애그리거트를 소유. list+detail 10 GET 전부 effective-dating(`asOf`) 지원, create/update/retire(+부서 move-parent) 16 write endpoint. 다른 3개 서비스의 업스트림 원천.',
    console: '마스터 · 개요(마스터 카운트)',
  },
  {
    key: 'approval-service',
    name: 'approval-service',
    context: '다단계 결재 워크플로 · 대결/위임 grant',
    desc: '결재 요청(ApprovalRequest) 생성·제출·승인·반려·철회, 1~N 단계(stage) 라우팅, 대결(위임) grant/revoke 를 소유. 결재 전이는 notification-service 로 이벤트 fan-out.',
    console: '결재함 · 위임 · 개요(결재 대기/활성 위임 카운트)',
  },
  {
    key: 'read-model-service',
    name: 'read-model-service',
    context: '조직도 투영 · 위임 fact 투영',
    desc: 'masterdata 이벤트를 소비해 직원 조직도(부서 계층 경로 + 원가센터 + 직급)와 위임 grant 상태를 eventually-consistent 하게 투영. 도메인 로직 없음(E5) — 원본은 항상 masterdata/approval.',
    console: '통합 조회 · 위임 현황(read-only 카드)',
  },
  {
    key: 'notification-service',
    name: 'notification-service',
    context: '결재 전이 인앱 알림',
    desc: '4개 `erp.approval.*` 전이 이벤트를 소비해 수신자 스코프 인앱 알림을 생성. 콘솔 셸의 알림 벨 aggregator 에 통합되며(ADR-043), ERP 독립 메뉴로는 노출하지 않는다.',
    console: '(콘솔 셸 알림 벨에 통합 — 독립 화면 없음)',
  },
];

// ───────────────────────── 콘솔 화면 맵 ─────────────────────────

/** 콘솔 화면 1개가 보여주는 값의 요약. */
export interface ConsoleScreen {
  key: string;
  label: string;
  route: string;
  desc: string;
}

/**
 * ERP 콘솔 6개 화면(TASK-PC-FE-232 정석 정렬 후: 개요 → 가이드 → 마스터 →
 * 통합 조회 → 결재함 → 위임).
 */
export const CONSOLE_SCREENS: ConsoleScreen[] = [
  {
    key: 'overview',
    label: '개요',
    route: '/erp',
    desc: '마스터 5종(부서·직원·직급·원가센터·거래처) 건수 + 결재 대기 건수(본인 inbox) + 활성 위임 건수를 집계한 카운트 타일. 각 타일은 독립적으로 degrade(한 타일의 503/403이 다른 타일을 가리지 않음).',
  },
  {
    key: 'guide',
    label: '가이드',
    route: '/erp/guide',
    desc: '이 화면. 도메인 서비스 구성과 6개 화면이 보여주는 값의 의미를 정적으로 설명한다.',
  },
  {
    key: 'masters',
    label: '마스터',
    route: '/erp/masters',
    desc: '부서·직원·직급·원가센터·거래처 5종 마스터 목록 조회 + 등록/수정/폐기(부서는 추가로 상위부서 이동). 전 목록·상세가 `?asOf=` 시점 조회를 지원(E3 effective-dating).',
  },
  {
    key: 'orgview',
    label: '통합 조회',
    route: '/erp/orgview',
    desc: 'read-model 이 투영한 직원 조직도 — 직원 + 소속 부서 계층 경로 + 원가센터 + 직급을 한 화면에서 조회. eventually-consistent(투영 지연 시 "동기화 중" 정직 표면).',
  },
  {
    key: 'approval',
    label: '결재함',
    route: '/erp/approval',
    desc: '결재 요청 생성/제출/승인/반려/철회. 1~N 단계 다단계 라우팅, 현재 단계 진행 타임라인, 대결(위임) 처리 시 실제 승인자 표시.',
  },
  {
    key: 'delegation',
    label: '위임',
    route: '/erp/delegation',
    desc: '대결(위임) grant 등록/철회 + read-model 기반 위임 현황(활성/철회 상태, 유효기간, 스코프) 조회.',
  },
];

// ───────────────────────── 마스터 상태 (Master status) ─────────────────────────

/** 마스터/직원 상태 1개. */
export interface StatusVocab {
  name: string;
  label: string;
  /** 운영자 주의가 필요한 상태인가(경고/종료). */
  attention: boolean;
  desc: string;
}

/**
 * 마스터(부서·직급·원가센터·거래처·직원 공통) `status` 2종. 콘솔은 이 상태를
 * **절대 숨기지 않고 있는 그대로** 표시한다(§ 2.4.8 E2 honesty) — 콘솔 소비
 * 순서는 `features/erp-ops/api/types.KNOWN_MASTER_STATUSES` 와 일치한다.
 */
export const MASTER_STATUSES: StatusVocab[] = [
  {
    name: 'ACTIVE',
    label: '활성',
    attention: false,
    desc: '현재 유효한 마스터(effectiveTo 가 null 또는 미래).',
  },
  {
    name: 'RETIRED',
    label: '폐기',
    attention: true,
    desc: '폐기된 마스터(effectiveTo 가 과거). 목록에서 시각적으로 구분되지만 절대 숨겨지지 않는다.',
  },
];

/**
 * 직원(Employee) `employmentStatus` 3종 —
 * `features/erp-ops/api/types.KNOWN_EMPLOYMENT_STATUSES` 와 일치.
 */
export const EMPLOYMENT_STATUSES: StatusVocab[] = [
  { name: 'EMPLOYED', label: '재직', attention: false, desc: '정상 재직 중.' },
  {
    name: 'ON_LEAVE',
    label: '휴직',
    attention: true,
    desc: '일시적 휴직 상태.',
  },
  {
    name: 'SEPARATED',
    label: '퇴사',
    attention: true,
    desc: '퇴사 처리됨. 마스터 목록에서 필터링되지 않고 그대로 표시된다(E2 honesty).',
  },
];

// ───────────────────────── 결재 상태머신 ─────────────────────────

/** 결재 상태 1개. */
export interface ApprovalStatusVocab {
  name: string;
  label: string;
  terminal: boolean;
  desc: string;
}

/**
 * 결재(ApprovalRequest) 상태 6종 —
 * `features/erp-ops/api/approval-types.APPROVAL_STATUSES` 와 일치. 경로:
 * `DRAFT → SUBMITTED → IN_REVIEW(2~N 단계) → APPROVED | REJECTED | WITHDRAWN`.
 */
export const APPROVAL_STATUSES: ApprovalStatusVocab[] = [
  {
    name: 'DRAFT',
    label: '초안',
    terminal: false,
    desc: '생성 직후 초기 상태. 제출(submit) 또는 철회(withdraw) 가능.',
  },
  {
    name: 'SUBMITTED',
    label: '제출됨',
    terminal: false,
    desc: '제출되어 첫 단계 승인자의 조치를 대기 중.',
  },
  {
    name: 'IN_REVIEW',
    label: '심사 중',
    terminal: false,
    desc: '2단계 이상 다단계 라우팅 중 현재 단계가 진행 중(v2.0).',
  },
  {
    name: 'APPROVED',
    label: '승인됨',
    terminal: true,
    desc: '모든 단계 승인 완료(happy terminal).',
  },
  {
    name: 'REJECTED',
    label: '반려됨',
    terminal: true,
    desc: '어느 단계에서든 반려되면 즉시 종료.',
  },
  {
    name: 'WITHDRAWN',
    label: '철회됨',
    terminal: true,
    desc: '제출자가 스스로 철회.',
  },
];

// ───────────────────────── 위임 스코프 ─────────────────────────

/** 위임 grant 스코프 1개. */
export interface DelegationScopeVocab {
  name: string;
  label: string;
  desc: string;
}

/**
 * 위임(Delegation) grant 의 `scope` 2종 —
 * `features/erp-ops/api/types/delegation-fact.ts` `DelegationFact.scope`
 * 와 일치(값 부재 시 out-of-order revoke-before-grant — scope 미상).
 */
export const DELEGATION_SCOPES: DelegationScopeVocab[] = [
  {
    name: 'GLOBAL',
    label: '포괄 위임',
    desc: '위임자(delegator) 명의의 모든 결재 요청에 대해 대결 가능.',
  },
  {
    name: 'REQUEST',
    label: '건별 위임',
    desc: '특정 결재 요청(scopeRequestId) 1건에 한정된 대결.',
  },
];

// ───────────────────────── 개념 노트 ─────────────────────────

/**
 * E3 — effective-dating 점-in-time 조회. 모든 마스터 list/detail 이
 * `?asOf=` 를 지원하며, 콘솔은 이를 first-class `<AsOfPicker>` 로 노출한다.
 */
export const ASOF_NOTE = {
  title: 'E3 — Effective-dating (`?asOf=`)',
  body: '모든 마스터의 list/detail GET 은 `?asOf=<ISO-8601>` 점-in-time 조회를 지원한다. 지정 시 producer 는 "현재 상태"가 아니라 "그 시점의 상태"를 정확히 반환한다(과거 조회에 현재 상태를 대체하는 것은 ERP UX 의 핵심 결함으로 취급). `/erp/masters` 상단 `<AsOfPicker>` 가 이 쿼리를 조작하는 유일한 통제점이며, 개요의 마스터 카운트 타일도 동일한 `asOf` 를 스레딩한다.',
} as const;

/**
 * 결재 다단계 라우팅 — v2.0 `approverIds` 순서 배열.
 */
export const APPROVAL_ROUTING_NOTE = {
  title: '다단계 결재 라우팅 (v2.0)',
  body: '결재 요청 생성 시 단일 승인자(`approverId`, 레거시) 또는 순서 있는 승인자 배열(`approverIds`, v2.0)을 지정할 수 있다. 후자는 1~N 단계 라우팅을 구성하며, 현재 단계(`currentStage`)의 승인자만 조치(승인/반려/철회) 가능하다. 각 단계 전이는 이력(`history`)에 append-only 로 기록되며, 대결(위임)로 처리된 경우 `actingForApproverId` 로 실제 승인자를 정직하게 표시한다.',
} as const;

/**
 * 위임(대결) grant — 결재자 부재 시 대신 처리할 수 있는 권한 위임.
 */
export const DELEGATION_NOTE = {
  title: '위임 (대결, Delegation)',
  body: '결재자(approver)가 부재 시 다른 운영자(delegate)가 대신 승인/반려하도록 grant 를 등록할 수 있다. grant 는 포괄(GLOBAL) 또는 건별(REQUEST) 스코프를 가지며, 유효기간(`validFrom`/`validTo`, `validTo` 부재 = 무기한)이 있다. 활성 grant 는 approval-service 가 결재 전이 시 조회하고, read-model 은 그 상태를 개요·위임 현황 화면에 eventually-consistent 하게 투영한다(개요의 "활성 위임" 타일은 `status=ACTIVE` 건수).',
} as const;

/**
 * 통합 조회 — read-model 의 eventually-consistent 투영, E5 원칙.
 */
export const READ_MODEL_NOTE = {
  title: '통합 조회 — read-model 투영 (E5)',
  body: 'read-model-service 는 masterdata 이벤트를 소비해 직원 조직도(부서 계층 경로 + 원가센터 + 직급)를 투영한다. 도메인 로직을 갖지 않는 순수 투영(E5)이므로 masterdata 대비 지연이 있을 수 있다 — 아직 투영되지 않은 참조는 `null` + `meta.unresolved` 로 "동기화 중" 배지가 표시되며, 절대 조작된 값으로 채워지지 않는다.',
} as const;

/**
 * 알림 — 결재 전이 이벤트가 콘솔 셸의 벨 aggregator 로 통합된다(ADR-043).
 */
export const NOTIFICATION_NOTE = {
  title: '알림 — 결재 전이는 벨 aggregator 로 통합',
  body: 'approval-service 의 4개 전이 이벤트(제출·승인·반려·철회)는 notification-service 가 소비해 수신자 스코프 인앱 알림을 생성한다. 콘솔은 이를 독립 ERP 메뉴가 아니라 콘솔 셸 상단의 공용 알림 벨에 통합해 노출한다(ADR-043 P2 DECLINED — 도메인별 독립 알림 화면 재발굴 금지). 벨에서 결재 알림을 클릭하면 해당 결재 요청(`/erp/approval`)으로 딥링크된다.',
} as const;

// ───────────────────────── 작업 레시피 (TASK-PC-FE-256) ─────────────────────────

/**
 * ERP 작업 레시피 — 결재 상태머신(반려→새 요청) · 위임 grant · effective-dating
 * (asOf)이라는 이 화면의 실제 상태·화면만 참조한다.
 */
export const ERP_RECIPES: GuideRecipeData[] = [
  {
    title: '결재가 반려됐을 때 다시 올리기',
    steps: [
      '결재함(/erp/approval)에서 반려된(REJECTED) 요청의 사유를 이력에서 확인합니다.',
      '반려는 종료 상태라 되돌릴 수 없으니, 내용을 고쳐 새 결재 요청을 생성합니다(DRAFT).',
      '제출(submit)하면 첫 단계 승인자부터 다시 라우팅됩니다(2단계 이상이면 IN_REVIEW 로 진행).',
    ],
  },
  {
    title: '자리를 비울 때 결재를 위임하기',
    steps: [
      '위임 화면(/erp/delegation)에서 대결(위임) grant 를 등록합니다.',
      '포괄(GLOBAL) 또는 건별(REQUEST) 스코프와 유효기간(validFrom/validTo, validTo 부재=무기한)을 정합니다.',
      '활성 grant 가 있으면 대리인이 대신 승인/반려할 수 있고, 처리 시 실제 승인자가 정직하게 표시됩니다(actingForApproverId).',
    ],
  },
  {
    title: '과거 시점의 조직 상태를 조회할 때',
    steps: [
      '마스터 화면(/erp/masters) 상단의 시점 선택기(AsOfPicker)에 조회할 날짜를 지정합니다.',
      '그러면 "현재"가 아니라 "그 시점"의 마스터 상태가 정확히 반환됩니다(effective-dating).',
      '개요의 마스터 카운트 타일도 같은 asOf 를 따라 집계됩니다.',
    ],
  },
];

// ───────────────────────── 용어집 (TASK-PC-FE-256) ─────────────────────────

/**
 * ERP 용어집 — 화면에 실제 렌더되는 문자열 중 일반 운영자가 모를 법한 용어만.
 * 결재 상태 enum(초안·제출됨…)·위임 스코프는 표가 이미 한글로 설명하므로 제외.
 */
export const ERP_GLOSSARY: GlossaryEntry[] = [
  {
    key: 'effective-dating',
    term: '시점 조회 (effective-dating)',
    meaning:
      'asOf 날짜를 지정하면 "현재"가 아니라 "그 시점"의 마스터 상태를 조회하는 기능. 과거 조회에 현재 상태를 대신 보여주지 않습니다.',
  },
  {
    key: 'read-model',
    term: '읽기모델 (read-model)',
    meaning:
      '원본 데이터를 이벤트로 투영해 만든 조회 전용 사본(조직도 등). 도메인 로직이 없고 원본보다 잠깐 지연될 수 있습니다.',
  },
  {
    key: 'eventually-consistent',
    term: '최종 일관성 (eventually-consistent)',
    meaning:
      '투영이 원본을 곧 따라잡지만 순간적으로는 과거일 수 있는 상태. 아직 반영 안 된 참조는 조작된 값 대신 "동기화 중"으로 표시됩니다.',
  },
  {
    key: 'delegation',
    term: '대결 (위임, delegation)',
    meaning:
      '결재자가 부재할 때 다른 운영자가 대신 승인·반려하도록 권한을 넘기는 것. 포괄(GLOBAL)·건별(REQUEST) 스코프와 유효기간을 가집니다.',
  },
];
