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
    desc: '부서·직원·직급·원가센터·거래처 같은 기준 정보를 등록·수정·폐기합니다. 과거 시점의 상태 조회도 이 서비스가 처리하며, 다른 서비스들은 모두 이 정보를 가져다 씁니다.',
    console: '마스터 · 개요(마스터 건수)',
  },
  {
    key: 'approval-service',
    name: 'approval-service',
    context: '결재 요청과 위임 처리',
    desc: '결재 요청을 만들고 제출·승인·반려·철회하는 과정을 처리합니다. 여러 단계를 거치는 결재와, 자리를 비울 때의 위임 등록도 이 서비스가 맡습니다. 결재가 진행되면 알림 서비스에 알려 줍니다.',
    console: '결재함 · 위임 · 개요(결재 대기/활성 위임 건수)',
  },
  {
    key: 'read-model-service',
    name: 'read-model-service',
    context: '직원 조직도 · 위임 현황 조회용 데이터',
    desc: '기준 정보가 바뀌면 그 내용을 받아 직원 조직도(부서·원가센터·직급)와 위임 현황을 보기 좋은 형태로 만들어 둡니다. 약간의 시간차가 있을 수 있어, 아직 반영되지 않은 내용은 화면에 "동기화 중"으로 표시됩니다.',
    console: '통합 조회 · 위임 현황(조회 전용 카드)',
  },
  {
    key: 'notification-service',
    name: 'notification-service',
    context: '결재 알림',
    desc: '결재가 제출·승인·반려·철회될 때마다 관련 운영자에게 알림을 보냅니다. ERP 안에 따로 메뉴가 있지는 않고, 콘솔 상단의 알림 벨에서 확인합니다.',
    console: '(독립 화면 없음 — 알림 벨에서 확인)',
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
 * ERP 콘솔 6개 화면(TASK-PC-FE-232 정석 정렬 후, TASK-PC-FE-297 가이드-먼저:
 * 가이드 → 개요 → 마스터 → 통합 조회 → 결재함 → 위임).
 */
export const CONSOLE_SCREENS: ConsoleScreen[] = [
  {
    key: 'overview',
    label: '개요',
    route: '/erp',
    desc: '부서·직원·직급·원가센터·거래처 건수와 내 결재 대기 건수, 활성 위임 건수를 한눈에 보여줍니다. 한 항목에 문제가 생겨도 다른 항목은 그대로 보입니다.',
  },
  {
    key: 'guide',
    label: '가이드',
    route: '/erp/guide',
    desc: '지금 보고 있는 이 화면입니다. ERP 의 구성과 각 화면에서 보는 값의 의미를 설명합니다.',
  },
  {
    key: 'masters',
    label: '마스터',
    route: '/erp/masters',
    desc: '부서·직원·직급·원가센터·거래처 목록을 보고 등록·수정·폐기합니다(부서는 상위 부서 변경도 가능). 날짜를 지정하면 과거 시점의 상태도 조회할 수 있습니다.',
  },
  {
    key: 'orgview',
    label: '통합 조회',
    route: '/erp/orgview',
    desc: '직원과 소속 부서, 원가센터, 직급을 한 화면에서 조회합니다. 최신 정보가 반영되기까지 잠깐 시간이 걸릴 수 있으며, 그 사이에는 "동기화 중"으로 표시됩니다.',
  },
  {
    key: 'approval',
    label: '결재함',
    route: '/erp/approval',
    desc: '결재 요청을 만들어 제출·승인·반려·철회합니다. 여러 단계를 거치는 결재도 지금 어느 단계인지 보여 주고, 위임받은 사람이 처리했으면 실제 처리자도 표시합니다.',
  },
  {
    key: 'delegation',
    label: '위임',
    route: '/erp/delegation',
    desc: '자리를 비울 때 대신 결재할 사람을 등록하거나 철회합니다. 지금 위임이 활성인지, 기간과 범위가 어떻게 되는지도 볼 수 있습니다.',
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
    desc: '지금 유효한 상태입니다.',
  },
  {
    name: 'RETIRED',
    label: '폐기',
    attention: true,
    desc: '더 이상 쓰이지 않는 상태입니다. 목록에서 구분되어 보이지만 숨겨지지는 않습니다.',
  },
];

/**
 * 직원(Employee) `employmentStatus` 3종 —
 * `features/erp-ops/api/types.KNOWN_EMPLOYMENT_STATUSES` 와 일치.
 */
export const EMPLOYMENT_STATUSES: StatusVocab[] = [
  { name: 'EMPLOYED', label: '재직', attention: false, desc: '정상적으로 재직 중입니다.' },
  {
    name: 'ON_LEAVE',
    label: '휴직',
    attention: true,
    desc: '일시적으로 휴직 중인 상태입니다.',
  },
  {
    name: 'SEPARATED',
    label: '퇴사',
    attention: true,
    desc: '퇴사한 직원입니다. 목록에서 걸러지지 않고 그대로 표시됩니다.',
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
    desc: '결재 요청을 막 만든 상태입니다. 제출하거나 철회할 수 있습니다.',
  },
  {
    name: 'SUBMITTED',
    label: '제출됨',
    terminal: false,
    desc: '제출되어 첫 번째 승인자의 처리를 기다리는 상태입니다.',
  },
  {
    name: 'IN_REVIEW',
    label: '심사 중',
    terminal: false,
    desc: '두 단계 이상인 결재가 진행 중인 상태입니다.',
  },
  {
    name: 'APPROVED',
    label: '승인됨',
    terminal: true,
    desc: '모든 단계가 승인되어 끝난 상태입니다.',
  },
  {
    name: 'REJECTED',
    label: '반려됨',
    terminal: true,
    desc: '어느 단계에서든 반려되면 바로 끝난 상태입니다.',
  },
  {
    name: 'WITHDRAWN',
    label: '철회됨',
    terminal: true,
    desc: '제출한 사람이 스스로 철회한 상태입니다.',
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
    desc: '위임한 사람의 모든 결재 요청을 대신 처리할 수 있습니다.',
  },
  {
    name: 'REQUEST',
    label: '건별 위임',
    desc: '특정 결재 요청 한 건만 대신 처리할 수 있습니다.',
  },
];

// ───────────────────────── 개념 노트 ─────────────────────────

/**
 * E3 — effective-dating 점-in-time 조회. 모든 마스터 list/detail 이
 * `?asOf=` 를 지원하며, 콘솔은 이를 first-class `<AsOfPicker>` 로 노출한다.
 */
export const ASOF_NOTE = {
  title: '날짜를 지정해 과거 상태 조회하기',
  body: '마스터 화면 위쪽에서 날짜를 지정하면, 지금이 아니라 그 날짜 기준의 상태를 보여줍니다. 날짜를 비워 두면 현재 상태가 보입니다. 개요의 마스터 건수도 같은 날짜를 기준으로 집계됩니다.',
} as const;

/**
 * 결재 다단계 라우팅 — v2.0 `approverIds` 순서 배열.
 */
export const APPROVAL_ROUTING_NOTE = {
  title: '여러 단계를 거치는 결재',
  body: '결재 요청에는 승인자를 한 명만 지정할 수도, 여러 명을 순서대로 지정할 수도 있습니다. 여러 단계인 경우 지금 차례인 승인자만 승인·반려·철회할 수 있습니다. 모든 처리 내용은 이력에 남고, 위임받은 사람이 처리했으면 실제 처리자도 함께 표시됩니다.',
} as const;

/**
 * 위임(대결) grant — 결재자 부재 시 대신 처리할 수 있는 권한 위임.
 */
export const DELEGATION_NOTE = {
  title: '위임(대결)',
  body: '결재자가 자리를 비울 때 다른 운영자가 대신 승인·반려하도록 등록할 수 있습니다. 전체 결재 요청에 대한 위임(포괄)이거나 특정 요청 한 건에 대한 위임(건별)일 수 있고, 기간을 정할 수 있습니다(기간을 정하지 않으면 무기한입니다). 개요의 "활성 위임" 건수는 지금 유효한 위임만 센 값입니다.',
} as const;

/**
 * 통합 조회 — read-model 의 eventually-consistent 투영, E5 원칙.
 */
export const READ_MODEL_NOTE = {
  title: '통합 조회는 어떻게 만들어지나요',
  body: '마스터 정보가 바뀌면 그 내용을 받아 직원 조직도를 다시 만들어 보여줍니다. 그래서 마스터 화면보다 반영이 조금 늦을 수 있습니다. 아직 반영되지 않은 부분은 "동기화 중"으로 표시되며, 실제와 다른 값을 억지로 채워 보여주지 않습니다.',
} as const;

/**
 * 알림 — 결재 전이 이벤트가 콘솔 셸의 벨 aggregator 로 통합된다(ADR-043).
 */
export const NOTIFICATION_NOTE = {
  title: '알림은 어떻게 오나요',
  body: '결재가 제출·승인·반려·철회될 때마다 관련 운영자에게 알림이 갑니다. ERP 안에 따로 알림 메뉴는 없고, 콘솔 상단의 알림 벨에서 다른 도메인 알림과 함께 확인합니다. 알림을 클릭하면 해당 결재 요청으로 바로 이동합니다.',
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
      '반려는 끝난 상태라 되돌릴 수 없으니, 내용을 고쳐 새 결재 요청을 만듭니다(DRAFT).',
      '제출하면 첫 번째 승인자부터 다시 진행됩니다(단계가 여러 개면 IN_REVIEW 상태가 됩니다).',
    ],
  },
  {
    title: '자리를 비울 때 결재를 위임하기',
    steps: [
      '위임 화면(/erp/delegation)에서 위임을 등록합니다.',
      '전체(GLOBAL) 또는 건별(REQUEST) 범위와 기간을 정합니다. 기간을 정하지 않으면 무기한입니다.',
      '위임이 활성 상태면 대리인이 대신 승인·반려할 수 있고, 처리하면 실제 처리자도 함께 표시됩니다.',
    ],
  },
  {
    title: '과거 시점의 조직 상태를 조회할 때',
    steps: [
      '마스터 화면(/erp/masters) 위쪽의 날짜 선택기에서 조회할 날짜를 지정합니다.',
      '그러면 지금이 아니라 그 날짜 기준의 상태를 보여줍니다.',
      '개요의 마스터 건수도 같은 날짜를 기준으로 집계됩니다.',
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
