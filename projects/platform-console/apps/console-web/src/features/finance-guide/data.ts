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
 * 🔵 **문장은 처음 보는 사람용이다**(TASK-PC-FE-323) — 쉬운 말, 카드 하나에
 * 한두 문장. 회계·규제 용어(차변/대변·KYC·대사·FX)는 남겨도 되지만 처음
 * 나올 때 쉬운 말로 짧게 풀어 쓴다. 클래스/메서드 이름·엔드포인트·필드명 같은
 * 구현 세부는 화면 운영에 필요하지 않으면 뺀다. 티켓/ADR 번호·파일 이름·
 * 「출처」 표시는 화면 문장에 쓰지 않는다(`tests/unit/domain-guides-plain.test.tsx`
 * 가 검사한다).
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

import type {
  GlossaryEntry,
  GuideRecipeData,
} from '@/shared/ui/guide-primitives';

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
    context: '계좌 · 잔액 · 거래 · KYC(고객확인)',
    desc: '계좌를 관리합니다. 계좌 개설(처음엔 KYC 대기 상태) · KYC 인증 올리기 · 자금 묶기(홀드) · 확정(캡처) · 풀기 · 이체 · 거래 내역 조회를 담당합니다.',
    console: '계좌',
  },
  {
    key: 'ledger-service',
    name: 'ledger-service',
    context: '회계 장부(복식부기) · 회계 기간 · 대사',
    desc: '계좌에서 일어난 거래를 넘겨받아 회계 장부에 기록합니다. 계정별 분개(장부 기록) · 시산표 · 회계 기간 마감 · 외부 명세서와의 대사 · 환율 정보를 관리합니다.',
    console: '원장 · 개요',
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
 * Finance 콘솔 4개 화면(TASK-PC-FE-229 정석 정렬 후, TASK-PC-FE-297 가이드-먼저:
 * 가이드 → 개요 → 계좌 → 원장). `/finance`(개요)는 계좌 목록/검색이 없는 finance v1 의 정직한 제약을
 * 지키기 위해 원장의 browsable read(시산표·기간·대사·FX)와 운영자 본인의
 * 기본계좌 **단건** 스냅샷만 집계한다 — cross-account 집계나 synthetic ₩
 * 합산은 절대 하지 않는다.
 */
export const CONSOLE_SCREENS: ConsoleScreen[] = [
  {
    key: 'overview',
    label: '개요',
    route: '/finance',
    desc: '시산표가 맞는지, 마감되지 않은 회계 기간이 몇 개인지, 해소되지 않은 대사 차이가 몇 건인지, 환율 정보가 최신인지를 한눈에 보여줍니다. 내 기본 계좌의 상태와 통화별 잔액도 함께 보입니다. 계좌 전체 목록은 보여주지 않습니다.',
  },
  {
    key: 'guide',
    label: '가이드',
    route: '/finance/guide',
    desc: '지금 보고 있는 이 화면입니다. Finance 가 어떤 서비스로 이루어져 있고, 각 화면이 무엇을 보여주는지 설명합니다.',
  },
  {
    key: 'accounts',
    label: '계좌',
    route: '/finance/accounts',
    desc: '계좌 번호를 입력해 조회합니다. 상태 · KYC 레벨 · 통화별 잔액(장부/가용/홀드)과 거래 내역을 볼 수 있습니다. 계좌 목록이나 검색 기능은 없어서, 번호를 알아야 조회할 수 있습니다.',
  },
  {
    key: 'ledger',
    label: '원장',
    route: '/ledger',
    desc: '시산표, 회계 기간별 마감 현황, 장부 기록(분개) 조회, 계정별 잔액과 기록 내역, 대사 대기 목록과 처리, 환율 정보를 확인하고 처리하는 화면입니다.',
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
    desc: '계좌를 개설하면 처음 갖는 상태입니다. KYC(고객확인) 인증을 올려야 활성 상태로 바뀝니다.',
  },
  {
    name: 'ACTIVE',
    label: '활성',
    attention: false,
    desc: '정상적으로 거래할 수 있는 상태입니다.',
  },
  {
    name: 'RESTRICTED',
    label: '제한',
    attention: true,
    desc: '규정 준수를 위한 조치로 일부 기능이 제한된 상태입니다.',
  },
  {
    name: 'FROZEN',
    label: '동결',
    attention: true,
    desc: '모든 거래가 막힌 상태입니다. 콘솔은 이 상태를 숨기지 않고 그대로 보여줍니다.',
  },
  {
    name: 'CLOSED',
    label: '종료',
    attention: false,
    desc: '계좌가 종료된 상태입니다. 종료 후에도 거래 내역은 볼 수 있습니다.',
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
  { name: 'NONE', label: '미인증', desc: '아직 인증하지 않은 기본 상태입니다. 거래 한도가 가장 낮습니다.' },
  { name: 'BASIC', label: '기본 인증', desc: '중간 수준의 거래 한도를 받습니다.' },
  { name: 'FULL', label: '완전 인증', desc: '가장 높은 거래 한도를 받습니다.' },
];

// ───────────────────────── 개념 노트 ─────────────────────────

/**
 * F5 — money 는 항상 minor-units **문자열**. `formatMoney` 만이 유일한 렌더
 * 경로다(§ 2.4.7 / § 2.4.7.1 contract obligation).
 */
export const F5_NOTE = {
  title: '금액은 항상 문자열로 표시됩니다',
  body: '계좌 잔액·거래·장부 기록·대사 차액·환율은 모두 숫자가 아니라 문자열로 전달됩니다(예: 원화는 소수점 없이, 달러는 소수점 둘째 자리까지). 이렇게 하면 아주 큰 금액(예: 1,234,567,890,123원)도 소수점 오차 없이 정확하게 표시됩니다.',
} as const;

/**
 * 복식부기 — 시산표의 `inBalance` 플래그가 차변/대변 합계 일치를 실시간으로
 * 증명한다.
 */
export const DOUBLE_ENTRY_NOTE = {
  title: '복식부기와 시산표',
  body: '모든 장부 기록(분개)은 차변과 대변의 합계가 항상 같아야 합니다. 이를 복식부기라고 합니다. 원장 화면의 시산표에서 이 합계가 맞는지 바로 확인할 수 있습니다 — 맞지 않으면 데이터에 문제가 있다는 뜻이며, 콘솔은 이를 숨기지 않고 그대로 보여줍니다. 개요 화면의 원장 타일에도 같은 값이 표시됩니다.',
} as const;

/**
 * 대사(reconciliation) — 외부 명세서와 내부 원장의 불일치를 OPEN 큐로
 * 노출하고, 운영자가 해소(resolve)한다.
 */
export const RECONCILIATION_NOTE = {
  title: '대사 — 명세서와 장부 맞춰보기',
  body: '외부 명세서(예: 은행이나 결제 정산 파일)를 우리 장부와 비교하는 것을 대사라고 합니다. 맞지 않는 내역(외부에만 있거나, 내부에만 있거나, 금액이 다른 경우 — 환율 차이 포함)은 "미해소(OPEN)" 상태로 쌓이고, 운영자가 확인해서 "해소(RESOLVED)" 상태로 바꿉니다. 개요 화면의 "미해소 대사 차이" 타일은 이 미해소 건수를 보여줍니다. 해소 처리는 원장 화면의 대사 목록에서만 할 수 있습니다.',
} as const;

/**
 * FX 환율 피드 신선도 — 개요/원장 모두 stale 여부를 정직하게 표시한다.
 */
export const FX_NOTE = {
  title: '환율 정보',
  body: '여러 통화를 다루는 장부는 외부에서 환율 정보를 주기적으로 받아와 사용합니다. 각 환율에는 기준 시각과 얼마나 오래됐는지가 함께 표시되며, 오래된 환율도 숨기지 않고 그대로 보여줍니다. 개요 화면의 환율 타일에는 오래된 환율이 몇 개인지가 요약되어 있습니다.',
} as const;

/**
 * 계좌 화면의 honest 제약 — finance v1 에는 계좌 list/search GET 이 없다.
 */
export const ACCOUNT_ID_DRIVEN_NOTE = {
  title: '계좌와 장부 기록은 번호로만 조회합니다',
  body: '계좌 화면은 계좌 번호를, 원장의 장부 기록 조회는 기록 번호를 입력해야 조회할 수 있습니다. 목록이나 검색 기능은 없습니다. 개요 화면이 전체 계좌 목록 대신 내 기본 계좌 하나만 보여주는 것도 같은 이유입니다.',
} as const;

// ───────────────────────── 작업 레시피 (TASK-PC-FE-256) ─────────────────────────

/**
 * Finance 작업 레시피 — 대사 해소(원장 화면의 유일한 쓰기) · 시산표 inBalance ·
 * 규제 계좌 상태/KYC 라는 이 화면의 실제 상태·화면만 참조한다. 콘솔에 없는 쓰기
 * (KYC 승급·동결 해제 등)는 "콘솔 범위 밖"으로 정직하게 안내한다.
 */
export const FINANCE_RECIPES: GuideRecipeData[] = [
  {
    title: '대사 차이를 해결할 때',
    steps: [
      '원장 화면(/ledger)의 대사 목록에서 "미해소(OPEN)" 상태인 항목을 엽니다.',
      '차이의 원인(외부에만 있는 내역 · 내부에만 있는 내역 · 금액이 다른 경우)을 확인합니다 — 금액이 다른 경우에는 환율 차이도 포함될 수 있습니다.',
      '처리하면 상태가 "해소(RESOLVED)"로 바뀝니다. 개요 화면의 "미해소 대사 차이" 타일은 이 미해소 건수를 보여줍니다.',
    ],
  },
  {
    title: '시산표가 맞지 않을 때',
    steps: [
      '개요 화면의 원장 타일이나 원장 화면의 시산표에서 합계가 맞는지 확인합니다.',
      '맞지 않으면 차변과 대변의 합계가 어긋난 것으로, 데이터에 문제가 있다는 뜻입니다. 콘솔은 이를 숨기지 않고 그대로 보여줍니다.',
      '원장 화면에서 계정별 합계와 개별 장부 기록을 확인해 원인을 찾습니다.',
    ],
  },
  {
    title: '계좌가 거래를 못 할 때',
    steps: [
      '계좌 화면(/finance/accounts)에서 계좌 번호로 상태를 조회합니다.',
      '상태가 동결이나 제한이거나, KYC 레벨이 낮으면(미인증/기본 인증) 거래가 막힐 수 있습니다.',
      'KYC 인증을 올리거나 동결을 해제하는 작업은 콘솔에서 할 수 없습니다 — 콘솔은 규제 상태를 있는 그대로 보여주기만 합니다.',
    ],
  },
];

// ───────────────────────── 용어집 (TASK-PC-FE-256) ─────────────────────────

/**
 * Finance 용어집 — 화면에 실제 렌더되는 회계·규제 전문 용어 중 일반 운영자가
 * 모를 법한 것만. 계좌 상태 enum(활성·동결…)은 표가 이미 한글로 설명하므로 제외.
 */
export const FINANCE_GLOSSARY: GlossaryEntry[] = [
  {
    key: 'KYC',
    term: 'KYC',
    full: 'Know Your Customer',
    meaning:
      '고객을 확인하는 절차입니다. 인증 레벨(미인증·기본·완전)이 낮으면 거래 한도가 낮아지거나 거래가 막힐 수 있습니다.',
  },
  {
    key: 'trial-balance',
    term: '시산표',
    meaning:
      '모든 계정의 차변·대변 합계를 모은 표입니다. 둘이 같으면 정상이고, 다르면 장부에 문제가 있다는 뜻입니다.',
  },
  {
    key: 'reconciliation',
    term: '대사',
    meaning:
      '외부 명세서(은행이나 결제 정산 파일 등)와 우리 장부를 비교하는 것입니다. 맞지 않는 내역은 목록에 쌓이고 운영자가 처리합니다.',
  },
  {
    key: 'FX',
    term: 'FX',
    full: 'Foreign Exchange',
    meaning:
      '환율입니다. 여러 통화를 다루는 장부는 외부에서 환율을 받아와 사용하며, 각 환율이 언제 적용된 것인지도 함께 보여줍니다.',
  },
  {
    key: 'minor-units',
    term: '최소 화폐단위',
    meaning:
      '금액을 소수점 오차 없이 정확하게 표현하는 방식입니다(원화는 소수점 없이, 달러는 소수점 둘째 자리까지). 콘솔은 이 값을 숫자로 바꾸지 않고 문자열 그대로 다룹니다.',
  },
];
