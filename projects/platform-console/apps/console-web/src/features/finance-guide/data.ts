/**
 * Finance 가이드 화면의 정적 참조 데이터 (TASK-PC-FE-229).
 *
 * Finance 콘솔의 라이브 화면 — **개요(`/finance`: 원장 집계 + 운영자 기본계좌
 * 스냅샷)** · **계좌(`/finance/accounts`: 계정·잔액·거래 조회)** · **원장
 * (`/ledger`: 시산표·기간·대사·FX)** · **가이드(`/finance/guide`, 이 화면)**
 * — 가 실제로 보여주는 값의 의미와, 그 뒤의 finance-platform 마이크로서비스
 * 구성을 운영자에게 설명한다. IAM 가이드(`features/iam-guide/data.ts`) ·
 * WMS 가이드(`features/wms-guide/data.ts`) · SCM 가이드
 * (`features/scm-guide/data.ts`) · E-Commerce 가이드
 * (`features/ecommerce-guide/data.ts`)와 같은 원칙: 타입 있는 정적 배열 +
 * 정적 화면. 데이터 페치·권한 게이트 없음(콘솔 진입자 누구나 열람).
 *
 * **SoT** (드리프트 시 이 파일 카피도 동반 갱신):
 *   - 계좌·잔액·거래·KYC: `finance-platform/specs/contracts/http/account-api.md`
 *     + `apps/account-service` 도메인 모델.
 *   - 원장·시산표·기간·복식부기: `finance-platform/specs/contracts/http/ledger-api.md`
 *     + `apps/ledger-service` 도메인 모델.
 *   - 대사(reconciliation): `finance-platform/specs/contracts/http/reconciliation-api.md`.
 *   - 콘솔 소비 타입(producer enum verbatim 반영 — 2차 SoT):
 *     `features/finance-ops/api/types.ts`(계좌·잔액·거래) ·
 *     `features/ledger-ops/api/types.ts`(시산표·기간·대사·FX) ·
 *     `features/finance-overview/api/overview-state.ts`(개요 집계).
 *
 * 테스트(FinanceGuideScreen.test.tsx)는 섹션/행 존재 등 **구조만** 단언하며,
 * 설명 텍스트 자체는 사람이 스펙과 맞춘다(iam/wms/scm/ecommerce guide data.ts
 * 동일 정책).
 */

// ───────────────────────── 도메인 서비스 맵 ─────────────────────────

/** finance-platform 마이크로서비스 1개. */
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
 * Finance 는 별도 게이트웨이 없이(v1, `account-service` 가 `/actuator/health`
 * 를 직접 노출) 2개 producer 로 구성된다 — 콘솔은 각 서비스를 도메인-facing
 * IAM OIDC 토큰으로 직접 호출한다(§ 2.4.7 / § 2.4.7.1). `account-service`
 * 는 계좌·잔액·거래·KYC 를, `ledger-service` 는 그 아래의 복식부기 원장(계좌
 * 별 분개는 아니고 회계 계정 코드 단위)을 소유한다 — 후자는 전자의 다운스트림
 * (거래가 발생하면 원장에 분개가 기록된다).
 */
export const DOMAIN_SERVICES: DomainService[] = [
  {
    key: 'account-service',
    name: 'account-service',
    context: '계좌 · 잔액 · 거래 · KYC/컴플라이언스',
    desc: '계좌(Account) 애그리거트를 소유. 계좌 개설(PENDING_KYC)·KYC 승급·홀드/캡처/해제·이체·거래 이력을 관리. v1 단일 배포(원장·대사는 ledger-service 로 분리).',
    console: '계좌',
  },
  {
    key: 'ledger-service',
    name: 'ledger-service',
    context: '복식부기 원장 · 회계 기간 · 대사',
    desc: 'account-service 하위 다운스트림. 회계 계정 코드(ledgerAccountCode) 단위 분개(journal entry)·시산표·회계 기간 마감·외부 명세서 대사(reconciliation)·FX 환율 피드를 소유.',
    console: '원장 · 개요(집계)',
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
 * Finance 콘솔 4개 화면(TASK-PC-FE-229 정석 정렬 후: 개요 → 가이드 → 계좌 →
 * 원장). `/finance`(개요)는 계좌 목록/검색이 없는 finance v1 의 정직한 제약을
 * 지키기 위해 원장의 browsable read(시산표·기간·대사·FX)와 운영자 본인의
 * 기본계좌 **단건** 스냅샷만 집계한다 — cross-account 집계나 synthetic ₩
 * 합산은 절대 하지 않는다.
 */
export const CONSOLE_SCREENS: ConsoleScreen[] = [
  {
    key: 'overview',
    label: '개요',
    route: '/finance',
    desc: '원장 집계 타일(시산표 inBalance · 미마감 기간 수 · 미해소 대사 차이 수 · FX 피드 신선도) + 운영자 본인의 기본 finance 계좌 단건 스냅샷(상태·통화별 잔액). 계좌 목록 집계는 하지 않는다(finance v1 에 계좌 list/search GET 없음).',
  },
  {
    key: 'guide',
    label: '가이드',
    route: '/finance/guide',
    desc: '이 화면. 도메인 서비스 구성과 4개 화면이 보여주는 값의 의미를 정적으로 설명한다.',
  },
  {
    key: 'accounts',
    label: '계좌',
    route: '/finance/accounts',
    desc: '계정(accountId) 조회 → 상태/KYC/통화 상세 + 통화별 잔액(장부/가용/holding) + 페이지네이션된 거래 이력(type·status 필터). accountId 입력 기반(목록/검색 없음 — 정직한 제약).',
  },
  {
    key: 'ledger',
    label: '원장',
    route: '/ledger',
    desc: '시산표(계정별 차변/대변 + 기준통화 총계 + inBalance) · 회계 기간 목록/상세(마감 스냅샷) · 분개(journal entry) id 조회 · 계정코드 드릴(잔액+분개 이력) · 대사 큐(OPEN 해소) · 대사 명세서 상세 · FX 환율 피드/history.',
  },
];

// ───────────────────────── 계좌 상태 (Account status) ─────────────────────────

/** 규제 계좌 상태 1개. */
export interface AccountState {
  name: string;
  label: string;
  /** 운영자 주의가 필요한 상태인가(경고/차단). */
  attention: boolean;
  desc: string;
}

/**
 * 계좌(Account) 상태 5종. `PENDING_KYC → ACTIVE` 가 정상 개설 경로이며, 이후
 * 컴플라이언스 조치로 `RESTRICTED`/`FROZEN` 이 되거나 `CLOSED` 로 종료될 수
 * 있다. 콘솔은 이 상태를 **절대 숨기지 않고 있는 그대로** 표시한다(§ 2.4.7
 * honest regulated-state surfacing) — 콘솔 소비 순서는
 * `features/finance-ops/api/types.KNOWN_ACCOUNT_STATUSES` 와 일치한다.
 */
export const ACCOUNT_STATES: AccountState[] = [
  {
    name: 'PENDING_KYC',
    label: 'KYC 대기',
    attention: true,
    desc: '계좌 개설 직후 초기 상태. KYC 승급(`kyc/upgrade`)이 있어야 ACTIVE 로 전환.',
  },
  {
    name: 'ACTIVE',
    label: '활성',
    attention: false,
    desc: '정상 거래 가능 상태.',
  },
  {
    name: 'RESTRICTED',
    label: '제한',
    attention: true,
    desc: '일부 기능이 제한된 컴플라이언스 조치 상태.',
  },
  {
    name: 'FROZEN',
    label: '동결',
    attention: true,
    desc: '전 거래가 차단된 하드 블록 상태. 콘솔은 이를 절대 숨기지 않는다.',
  },
  {
    name: 'CLOSED',
    label: '종료',
    attention: false,
    desc: '계좌 종료(비활성). 종료 상태 — 거래 이력은 조회 가능.',
  },
];

// ───────────────────────── KYC 레벨 ─────────────────────────

/** KYC 레벨 1개. */
export interface KycLevel {
  name: string;
  label: string;
  desc: string;
}

/**
 * KYC(고객확인) 레벨 3종. 낮은 레벨은 거래 한도가 낮거나 홀드/이체가
 * `403 KYC_REQUIRED`/`KYC_LEVEL_INSUFFICIENT` 로 차단될 수 있다(운영자 승급
 * 액션은 콘솔 범위 밖 — account-service `kyc/upgrade` 는 v1 콘솔에 노출되지
 * 않는 write 이다).
 */
export const KYC_LEVELS: KycLevel[] = [
  { name: 'NONE', label: '미인증', desc: '기본값. 거래 한도가 가장 낮다.' },
  { name: 'BASIC', label: '기본 인증', desc: '중간 수준의 거래 한도.' },
  { name: 'FULL', label: '완전 인증', desc: '가장 높은 거래 한도.' },
];

// ───────────────────────── 개념 노트 ─────────────────────────

/**
 * F5 — money 는 항상 minor-units **문자열**. `formatMoney` 만이 유일한 렌더
 * 경로다(§ 2.4.7 / § 2.4.7.1 contract obligation).
 */
export const F5_NOTE = {
  title: 'F5 — 금액은 항상 문자열(minor units)',
  body: '계좌 잔액·거래·원장 분개·대사 차액·FX 환율은 전부 정밀도 손실 없는 minor-units **문자열**로 전달된다(KRW=0자리, USD=2자리 등). 콘솔은 `Number()`/`parseFloat()`/`parseInt()` 로 절대 변환하지 않고 `formatMoney(...)` 문자열 조작만으로 스케일 보정 렌더한다 — 큰 금액(예: KRW 1,234,567,890,123)에서 부동소수점 정밀도 손실을 방지하기 위함이다.',
} as const;

/**
 * 복식부기 — 시산표의 `inBalance` 플래그가 차변/대변 합계 일치를 실시간으로
 * 증명한다.
 */
export const DOUBLE_ENTRY_NOTE = {
  title: '복식부기 — 시산표 inBalance',
  body: '모든 분개(journal entry)는 차변(debit)과 대변(credit) 합계가 항상 같아야 한다는 복식부기 불변식을 따른다. 원장 화면의 시산표(trial balance)는 전 계정의 차변/대변 합계(및 기준통화 KRW 환산 총계)를 집계해 `inBalance` 플래그로 즉시 증명한다 — `false` 면 데이터 정합성 문제이며 절대 숨기지 않는다. 개요의 원장 타일도 이 값을 그대로 표시한다.',
} as const;

/**
 * 대사(reconciliation) — 외부 명세서와 내부 원장의 불일치를 OPEN 큐로
 * 노출하고, 운영자가 해소(resolve)한다.
 */
export const RECONCILIATION_NOTE = {
  title: '대사 (Reconciliation) — OPEN 큐 · 해소',
  body: '외부 명세서(예: 은행/PG 정산 파일)를 원장에 대사(matching)한 결과, 매칭되지 않은 외부 항목(UNMATCHED_EXTERNAL)·내부 항목(UNMATCHED_INTERNAL)·금액이 다른 매칭 쌍(AMOUNT_MISMATCH, FX 차액 포함)이 있으면 대사 차이(discrepancy)로 기록된다. 상태는 OPEN(미해소) → RESOLVED(해소) 이며, 개요의 "미해소 대사 차이 수" 타일은 OPEN 상태의 개수를 집계한 것이다. 해소 액션(원장 화면의 유일한 쓰기)은 이 가이드가 아니라 원장 화면(`/ledger`)의 대사 큐에서 수행한다.',
} as const;

/**
 * FX 환율 피드 신선도 — 개요/원장 모두 stale 여부를 정직하게 표시한다.
 */
export const FX_NOTE = {
  title: 'FX 환율 피드 — 신선도(freshness)',
  body: '다중 통화 원장은 외부 FX 환율 피드를 주기적으로 폴링해 캐시한다. 각 환율은 `asOf`(기준 시각)·`ageSeconds`(경과)·`stale`(신선도 기준 초과 여부)을 함께 제공하며, 콘솔은 오래된(stale) 환율을 숨기지 않고 그대로 표시한다 — 개요의 FX 타일은 피드 활성화 여부와 오래된 환율 수를 요약한다.',
} as const;

/**
 * 계좌 화면의 honest 제약 — finance v1 에는 계좌 list/search GET 이 없다.
 */
export const ACCOUNT_ID_DRIVEN_NOTE = {
  title: '계좌·원장 분개는 목록 조회가 없다 (id-driven)',
  body: 'finance v1 은 계좌(account-service)·분개(ledger-service) 모두 list/search GET 을 제공하지 않는다 — 계좌 화면은 accountId 를, 원장의 분개 조회는 entryId 를 입력받는 조회 전용 화면이다. 개요가 "계좌 목록 요약"이 아니라 "운영자 본인의 기본계좌 단건 스냅샷"만 보여주는 이유도 이 제약 때문이다(cross-account 집계·synthetic 합산 금지).',
} as const;
