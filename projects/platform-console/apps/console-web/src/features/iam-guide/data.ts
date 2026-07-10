/**
 * IAM 가이드 화면의 정적 참조 데이터 (TASK-PC-FE-163, 재구성 TASK-PC-FE-238).
 *
 * **SoT**: `projects/iam-platform/specs/services/admin-service/rbac.md`
 * (§ Permission Keys / § Seed Roles / § Seed Matrix). 이 파일은 그 스펙의
 * seed 매트릭스를 화면-표시용으로 옮긴 것이며, admin-service seed(Flyway
 * `V0033__seed_tenant_admin_roles.sql` 외)가 canonical 이다. seed role/권한이
 * 바뀌면 이 파일도 동반 갱신해야 한다(테스트가 role×화면 매트릭스를 단언하여
 * 표면적 드리프트는 잡지만, 설명 텍스트는 사람이 맞춰야 함).
 *
 * 화면은 세 부분으로 나뉜다(PC-FE-238):
 *   1. 개념     — `ACCOUNT_HATS` · `AUTH_PLANES`
 *   2. 사용법   — `CONSOLE_MENUS` · `DELEGATION_CHAIN` · `OPERATOR_ONBOARDING_AXES`
 *   3. 레퍼런스 — `SEED_ROLES` · `SCREEN_ACCESS` · `PERMISSION_KEYS` · `DOMAIN_ROLE_MAP`
 *
 * **알려진 누락 — `ORG_ADMIN` / `org.manage` (ADR-MONO-047)**: `rbac.md` 는
 * 7번째 seed role `ORG_ADMIN` 과 `org.manage` 키를 정의하지만 이 파일은 6개 role
 * 만 다룬다. PC-FE-238 이 이 절을 쓸 당시엔 `/org-hierarchy` 화면이 없었고,
 * 존재하지 않는 메뉴를 가이드가 설명할 수는 없었다.
 *
 * **그 전제는 이제 거짓이다** — `TASK-PC-FE-237`(PR #2384)이 `/org-hierarchy`
 * 화면을 실제로 구현했다. 따라서 지금 이 누락은 스코프 결정이 아니라 **드리프트**
 * 이며, `TASK-PC-FE-239` 가 `ORG_ADMIN` 열 + `org.manage` 행 + 메뉴 엔트리를
 * 추가한다. (role 열 추가는 `SEED_ROLES`·`SCREEN_ACCESS`·`PERMISSION_KEYS`·
 * `CONSOLE_MENUS` 네 구조와 매트릭스를 DOM 위치로 단언하는 가이드 테스트를 함께
 * 움직이므로 별도 task 로 분리했다.)
 */

/* ─────────────────────────── 1. 개념 ─────────────────────────── */

/**
 * 하나의 계정, 4개의 모자 (TASK-PC-FE-202) — 하나의 통합 IAM 계정(`account_id`)이
 * 처한 관계에 따라 쓰는 4가지 인가 유형. 콘솔 진입 전 큰 그림을 잡아주는
 * orientation 이며, 이 화면의 나머지(역할·배정·도메인 롤)는 ②~④ 모자의 세부다.
 *
 * **SoT**: repo 개념 가이드 `iam-platform/docs/guides/operator-auth-token-model.md`
 * § 6 "하나의 계정, 4개의 모자"(TASK-BE-482) + `admin-service/rbac.md`(role 모델) +
 * ADR-MONO-045(cross-org 파트너십 cap). 이 배열은 그 개념의 화면-표시용 미러이며,
 * 토큰 축(1축 로그인 / operator token / 2축 assume-tenant) 자체는 repo 가이드가 권위.
 *
 * 축 구분: **② ↔ ③** = owner(조직을 세팅) vs assigned operator(배정 범위를 운영),
 * **③ ↔ ④** = intra-org 배정(내 회사 테넌트, 자연 배정) vs cross-org 파트너십
 * (남의 회사 테넌트, scope cap · admin 불가).
 */
export interface AccountHat {
  /** ①~④ 순서 마커. */
  marker: string;
  /** 관계 한글명. */
  relation: string;
  /** 정체성/역할 한 줄. */
  role: string;
  /** 이 모자에 필요한 로그인/권한 요약. */
  token: string;
  /** 콘솔에서 어디로 관리/진입하는지. */
  consoleNote: string;
}

export const ACCOUNT_HATS: AccountHat[] = [
  {
    marker: '①',
    relation: '소비자',
    role: '쇼핑만 하는 회원 — 운영자가 아님',
    token: '로그인만',
    consoleNote: '콘솔에 들어오지 않습니다.',
  },
  {
    marker: '②',
    relation: '내가 차린 회사',
    role: '테넌트 주인 — 조직을 세팅하는 사람',
    token: '로그인 + 운영자 권한',
    consoleNote: '도메인 구독 · 운영자 등록 · 파트너십 위임.',
  },
  {
    marker: '③',
    relation: '내가 다니는 회사',
    role: '배정받은 직원 — 맡은 도메인을 운영',
    token: '+ 테넌트 선택(도메인 권한이 여기서 생김)',
    consoleNote: '테넌트를 고른 뒤 WMS · 이커머스 등 도메인 화면.',
  },
  {
    marker: '④',
    relation: '내 회사가 일해주는 다른 회사',
    role: '협력사 담당자 — 위임받은 범위 안에서만',
    token: '+ 테넌트 선택(범위가 더 좁게 제한됨)',
    consoleNote: '위임받은 도메인만 · 상대 회사를 관리할 수는 없음.',
  },
];

/**
 * 권한 두 평면 — operator token 이 담는 admin RBAC 롤과 assume-tenant 가 담는
 * 도메인 롤은 서로 다른 평면에 살고, 절대 교차하지 않는다(disjoint). 이 화면의
 * 레퍼런스 절에서 전자는 "역할 6종", 후자는 "구독 도메인 → 도메인 롤" 표가 각각
 * 상세히 다루며, 이 배열은 둘을 한눈에 대비한다.
 *
 * SoT: iam-platform/docs/guides/operator-auth-token-model.md § 6 +
 * ADR-MONO-035("SUPER_ADMIN is not WMS_OPERATOR" — 평면 분리 불변식).
 */
export interface AuthPlane {
  /** 평면 한글명. */
  koName: string;
  /** 이 평면이 언제 붙는지(표시용 짧은 라벨 — testid 키로도 쓰인다). */
  token: string;
  /** 무엇을 여는 권한인가. */
  purpose: string;
  /** 롤이 어디서 오는가. */
  storage: string;
  /** 대표 롤(표시용 문자열). */
  roles: string;
}

export const AUTH_PLANES: AuthPlane[] = [
  {
    koName: '관리 권한',
    token: '로그인할 때 붙음',
    purpose: 'IAM 콘솔 메뉴(운영자 · 계정 · 감사 · 테넌트 …)를 엽니다.',
    storage: '관리자가 직접 부여하고, 그대로 저장됩니다.',
    roles:
      '플랫폼: SUPER_ADMIN · SUPPORT_READONLY · SUPPORT_LOCK · SECURITY_ANALYST / 테넌트: TENANT_ADMIN · TENANT_BILLING_ADMIN',
  },
  {
    koName: '도메인 운영 권한',
    token: '테넌트를 고를 때 붙음',
    purpose: '도메인 화면(WMS · 이커머스 · SCM …)을 엽니다.',
    storage: '아무도 부여하지 않습니다 — 그 테넌트의 구독 도메인에서 자동으로 생깁니다.',
    roles:
      'WMS_OPERATOR · ECOMMERCE_OPERATOR · SCM_OPERATOR · ERP_OPERATOR · FINANCE_OPERATOR · MES_OPERATOR · FAN_OPERATOR',
  },
];

/**
 * 두 평면이 섞이지 않는다는 불변식 (ADR-MONO-035). 스펙 용어("disjoint")를 쓰지
 * 않고 결과만 말한다 — 화면 카피는 평이해야 한다(PC-FE-238 AC-5).
 */
export const AUTH_PLANE_DISJOINT =
  '두 권한은 섞이지 않습니다. 관리 권한이 아무리 세도 도메인 화면이 열리지 않고, 도메인 권한이 있어도 IAM 메뉴는 열리지 않습니다 — SUPER_ADMIN 이라고 WMS 운영자가 되지는 않습니다.';

/* ─────────────────────────── 2. 사용법 ─────────────────────────── */

/**
 * IAM 관련 콘솔 메뉴 카탈로그 (TASK-PC-FE-238) — 사이드바 「관리 ▸ IAM」 +
 * 「고객 신원」 + 「조직 설정」 그룹의 목적지 전부. 각 화면이 실제로 제공하는
 * 조작(버튼/다이얼로그)과 그것을 여는 권한 키를 한 줄로 요약한다.
 *
 * 순서는 `ConsoleSidebarNav.tsx` 의 nav 순서(orient → learn → configure →
 * operate → review)를 따른다. `gate` 는 **화면을 여는** 데 필요한 키이며, 화면
 * 안의 개별 액션이 별도 키를 요구하는 경우 `note` 에 적는다.
 *
 * SoT: 각 라우트 `page.tsx` 의 JSDoc + admin-service `@RequiresPermission`
 * 애노테이션 + `rbac.md` § Permission Keys.
 */
export interface ConsoleMenu {
  /** 사이드바 라벨. */
  label: string;
  /** 라우트(참조용 + testid 키). */
  href: string;
  /** 이 화면은 무엇을 하는 곳인가 — 한 줄. */
  purpose: string;
  /** 여기서 할 수 있는 조작. 읽기 전용이면 조회 항목을 적는다. */
  actions: string;
  /** 화면을 여는 데 필요한 권한 키(표시용). 스텁/무게이트는 `—`. */
  gate: string;
  /** 변경(쓰기) 화면인지 — 조회 전용 화면과 시각적으로 구분한다. */
  mutates: boolean;
  /** 아직 기능이 없는 스텁 메뉴. */
  stub?: boolean;
  /** 비직관적인 점 하나(선택). */
  note?: string;
}

export const CONSOLE_MENUS: ConsoleMenu[] = [
  {
    label: '개요',
    href: '/iam',
    purpose: '운영자 · 계정 · 감사 현황을 한 화면에서 요약해 봅니다.',
    actions: '현황 카드 조회 → 각 화면으로 이동',
    gate: '카드별로 다름',
    mutates: false,
    note: '권한이 없는 카드만 "권한 없음"으로 표시되고 나머지는 정상 렌더됩니다.',
  },
  {
    label: '운영자 관리',
    href: '/operators',
    purpose: '운영자를 등록하고 역할 · 상태 · 담당 범위를 정합니다.',
    actions:
      '운영자 등록 · 역할 변경 · 사용중지/재개 · 테넌트 배정/해제 · 조직 범위(부서) 지정',
    gate: 'operator.manage',
    mutates: true,
    note: '모든 변경은 사유 입력이 필수이고 감사 로그에 남습니다. 자기가 가진 것보다 높은 역할은 부여할 수 없습니다.',
  },
  {
    label: '운영자 그룹',
    href: '/operator-groups',
    purpose: '여러 운영자를 묶어 역할을 한 번에 부여합니다.',
    actions: '준비 중 — 아직 기능이 없습니다.',
    gate: '—',
    mutates: false,
    stub: true,
  },
  {
    label: '테넌트',
    href: '/tenants',
    purpose: '테넌트(회사 단위 격리 경계)를 만들고 관리합니다.',
    actions: '테넌트 생성 · 표시명/상태 변경 · 상세 조회 · 상태/유형 필터',
    gate: 'tenant.manage',
    mutates: true,
    note: '조회에도 같은 권한이 필요해 사실상 SUPER_ADMIN 전용입니다. 상단의 테넌트 전환기(내가 지금 어느 테넌트로 일하는가)와는 다른 화면입니다.',
  },
  {
    label: '권한',
    href: '/permissions',
    purpose: '권한 키가 무엇이 있고 어떤 역할이 갖는지 찾아봅니다.',
    actions: '조회 전용 — 역할을 펼치면 보유 권한 키가 보입니다.',
    gate: 'operator.manage',
    mutates: false,
  },
  {
    label: '권한 세트',
    href: '/permission-sets',
    purpose: '테넌트 배정에 붙일 수 있는 권한 묶음(= 역할)을 봅니다.',
    actions: '조회 전용 — 세트를 펼치면 포함된 권한 키가 보입니다.',
    gate: 'operator.manage',
    mutates: false,
    note: '「권한」 화면과 같은 데이터를 배정 관점으로 다시 보여주는 것입니다.',
  },
  {
    label: '감사 · 보안',
    href: '/audit',
    purpose: '누가 무엇을 했는지, 로그인 이력과 의심 활동을 조회합니다.',
    actions:
      '조회 전용 — 계정 · 액션 · 기간 · 출처로 필터. 조회 자체도 기록에 남습니다.',
    gate: 'audit.read',
    mutates: false,
    note: '로그인 이력 · 의심 활동 출처는 security.event.read 가 추가로 필요하며, 없으면 그 필터만 잠깁니다.',
  },
  {
    label: '계정 운영',
    href: '/accounts',
    purpose: '서비스 전체가 공유하는 사용자 계정을 잠그고 세션을 정리합니다.',
    actions:
      '이메일 검색 · 잠금/해제 · 여러 건 일괄 잠금 · 세션 강제 종료 · 데이터 내보내기 · GDPR 삭제',
    gate: 'account.read',
    mutates: true,
    note: '내보내기는 audit.read, GDPR 삭제는 account.lock 으로 게이트됩니다(이름과 직관이 어긋나니 주의). GDPR 삭제는 되돌릴 수 없습니다.',
  },
  {
    label: '도메인 구독',
    href: '/subscriptions',
    purpose: '내 테넌트가 어떤 도메인(WMS · 이커머스 …)을 쓸지 켜고 끕니다.',
    actions: '구독 · 일시중지 · 재개 · 해지',
    gate: 'subscription.manage',
    mutates: true,
    note: '새 테넌트는 구독 0건에서 시작합니다. 여기서 켠 도메인이 곧 직원들의 도메인 운영 권한이 됩니다.',
  },
  {
    label: '파트너십',
    href: '/partnerships',
    purpose: '다른 회사에 우리 테넌트의 일부 운영을 위임하거나, 위임받습니다.',
    actions:
      '초대 · 수락 · 일시중지 · 재개 · 종료 · (위임받은 쪽) 담당 운영자 배정/해제',
    gate: 'partnership.manage',
    mutates: true,
    note: 'SUPER_ADMIN 도 열 수 없습니다 — 파트너십은 두 고객사 사이의 관계이고 플랫폼은 당사자가 아니기 때문입니다.',
  },
  {
    label: '가이드',
    href: '/iam/guide',
    purpose: '지금 보고 있는 이 화면입니다.',
    actions: '조회 전용 — 누구나 열 수 있습니다.',
    gate: '—',
    mutates: false,
  },
];

/** 운영자를 온보딩할 때 실제로 일어나는 3단계. */
export interface DelegationStep {
  actor: string;
  action: string;
}

export const DELEGATION_CHAIN: DelegationStep[] = [
  {
    actor: 'SUPER_ADMIN (플랫폼 운영자)',
    action:
      'a회사의 관리자 운영자를 만들고 TENANT_ADMIN 역할을 줍니다. 「운영자 관리」에서 등록 → 역할 부여.',
  },
  {
    actor: 'a회사 TENANT_ADMIN',
    action:
      '자기 회사 직원을 운영자로 등록하고, 일할 테넌트에 배정합니다. 필요하면 관리자를 한 명 더 세울 수도 있습니다. 모두 자기 테넌트 안에서만 가능합니다.',
  },
  {
    actor: 'a회사 직원 · 협력사',
    action:
      '로그인한 뒤 테넌트를 고르면, 그 테넌트가 구독 중인 도메인의 운영 권한이 자동으로 붙어 WMS · 이커머스 화면에 들어갑니다.',
  },
];

/** 위임할 때 항상 걸리는 두 가지 안전장치 (ADR-MONO-024 D2/D3). */
export const DELEGATION_GUARDS: { name: string; desc: string }[] = [
  {
    name: '내 테넌트 밖은 건드릴 수 없다',
    desc: '플랫폼 역할이 아닌 운영자는 자기 테넌트의 대상만 바꿀 수 있습니다. 다른 테넌트를 건드리면 거부됩니다. SUPER_ADMIN 만 예외입니다.',
  },
  {
    name: '내가 가진 것보다 크게 줄 수 없다',
    desc: '자신이 갖지 않은 권한은 남에게 줄 수 없고, SUPER_ADMIN 은 아무도 부여할 수 없습니다. TENANT_ADMIN 은 별도 위임 권한이 있을 때만 또 다른 TENANT_ADMIN 을 세울 수 있습니다.',
  },
];

/**
 * 운영자 온보딩 3축 (home tenant / tenant assignment / org_scope).
 *
 * 위임 체인이 "누가 어떤 role 을 부여하는가"라면, 이 3축은 그 결과로 운영자의
 * **운영 도달 범위**가 어떻게 정해지는가를 정리한 것이다 — 서로 직교한다.
 *
 * SoT: console-web operators 피처(`operators-assignments-api.ts`) +
 * admin-service `operator_tenant_assignment`(홈 테넌트는 운영자 생성 시
 * tenantId, 이후 불변; org_scope 3-상태는 BE-338 fail-closed 시맨틱).
 */
export interface OnboardingAxis {
  /** 축 이름(영문 용어 — 코드/도메인 통용어, testid 키). */
  term: string;
  /** 사람이 읽는 한글 요약명. */
  koName: string;
  /** 어디서 정해지는가. */
  api: string;
  /** 한 줄 의도. */
  desc: string;
  /** 이커머스 운영에서의 구체 예 — 이 축이 실제로 어떻게 쓰이는지. */
  ecommerceNote: string;
}

export const OPERATOR_ONBOARDING_AXES: OnboardingAxis[] = [
  {
    term: 'home tenant',
    koName: '원 소속',
    api: '운영자를 만들 때 정해지고, 이후 바뀌지 않습니다.',
    desc: '이 운영자가 어느 회사 사람인가. 플랫폼 소속이면 모든 테넌트를 제약 없이 지원하고, 특정 회사 소속이면 그 회사가 기준입니다. 배정이 없으면 원 소속에서만 일합니다.',
    ecommerceNote:
      '플랫폼 CS 팀은 모든 이커머스 테넌트를 지원하고, 입점사 직원은 그 입점사 테넌트를 원 소속으로 갖습니다.',
  },
  {
    term: 'tenant assignment',
    koName: '테넌트 배정',
    api: '「운영자 관리」 → 배정 / 배정 해제',
    desc: '원 소속이 아닌 회사에서도 일할 수 있게 문을 열어주는 유일한 방법입니다. 배정하면 로그인 후 그 테넌트를 고를 수 있게 됩니다. 사유 입력이 필요합니다.',
    ecommerceNote:
      '입점사 운영을 대행하는 협력사 담당자를 그 이커머스 테넌트에 배정하면, 테넌트를 골라 이커머스 운영 화면에 들어갑니다.',
  },
  {
    term: 'org_scope',
    koName: '조직 범위(부서)',
    api: '「운영자 관리」 → 조직 범위 지정',
    desc: '배정은 그대로 두고 볼 수 있는 부서 데이터만 좁힙니다. 세 가지 상태가 있습니다 — 전체(제한 없음) · 지정한 부서와 그 하위만 · 아무것도 못 봄. "전체"와 "아무것도 못 봄"은 정반대이니 혼동하지 마세요.',
    ecommerceNote:
      '협력사 담당자를 특정 부서로 좁혀두면, 배정은 유지한 채 그 부서 데이터만 보게 됩니다.',
  },
];

/* ─────────────────────────── 3. 레퍼런스 ─────────────────────────── */

/** admin-service 가 평가하는 권한 키 (rbac.md § Permission Keys). */
export interface PermissionKey {
  key: string;
  desc: string;
}

export const PERMISSION_KEYS: PermissionKey[] = [
  { key: 'account.read', desc: '계정 목록을 조회합니다.' },
  { key: 'account.lock', desc: '계정을 잠급니다. GDPR 삭제도 이 키로 게이트됩니다.' },
  { key: 'account.unlock', desc: '계정 잠금을 풉니다.' },
  {
    key: 'account.force_logout',
    desc: '그 계정의 모든 세션을 강제로 끊습니다.',
  },
  {
    key: 'audit.read',
    desc: '감사 로그를 조회합니다. 계정 데이터 내보내기도 이 키로 게이트됩니다.',
  },
  {
    key: 'security.event.read',
    desc: '로그인 이력과 의심 활동을 조회합니다.',
  },
  {
    key: 'operator.manage',
    desc: '운영자를 등록 · 역할 부여 · 상태 변경 · 테넌트 배정하고, 권한 카탈로그를 읽습니다.',
  },
  {
    key: 'tenant.manage',
    desc: '테넌트를 조회 · 생성 · 변경합니다. 플랫폼 운영자 전용입니다.',
  },
  {
    key: 'subscription.manage',
    desc: '테넌트가 어떤 도메인을 쓸지 구독을 관리합니다.',
  },
  {
    key: 'tenant.admin.delegate',
    desc: '자기 테넌트 안에서 TENANT_ADMIN 을 한 명 더 세웁니다.',
  },
  {
    key: 'partnership.manage',
    desc: '자기 테넌트가 당사자인 회사 간 파트너십을 관리합니다. SUPER_ADMIN 은 갖지 않습니다.',
  },
];

/** admin-console RBAC seed role (rbac.md § Seed Roles). */
export interface SeedRole {
  name: string;
  /** 한 줄 의도. */
  intent: string;
  /** 사람이 읽는 한글 역할명(부제) — 화면에서 role.name 옆에 표시. */
  koName: string;
  /**
   * 스코프 평면. `platform` = 플랫폼 전역 운영 role(SUPER_ADMIN 및 플랫폼 측
   * CS·보안팀 — grant 가 플랫폼 스코프로 프로비저닝됨). `tenant` = grant row 의
   * `tenant_id` 로 자기 테넌트에 confine 되는 위임 role(ADR-MONO-024 D2).
   */
  scope: 'platform' | 'tenant';
  /** 보유 권한 키 집합(합집합 평가). */
  permissions: string[];
  /** SUPER_ADMIN 처럼 특권 role 은 시각적으로 구분. */
  elevated?: boolean;
}

export const SEED_ROLES: SeedRole[] = [
  {
    name: 'SUPER_ADMIN',
    koName: '플랫폼 관리자',
    scope: 'platform',
    intent:
      '모든 테넌트를 제약 없이 관리합니다. 단, 파트너십에는 관여하지 않습니다.',
    permissions: [
      'account.read',
      'account.lock',
      'account.unlock',
      'account.force_logout',
      'audit.read',
      'security.event.read',
      'operator.manage',
      'tenant.manage',
      'subscription.manage',
    ],
    elevated: true,
  },
  {
    name: 'SUPPORT_READONLY',
    koName: 'CS 상담원',
    scope: 'platform',
    intent:
      '조회만 합니다 — 계정 · 감사 · 보안 이벤트. 아무것도 바꾸지 못합니다.',
    permissions: ['account.read', 'audit.read', 'security.event.read'],
  },
  {
    name: 'SUPPORT_LOCK',
    koName: 'CS 상담원 (계정 제어)',
    scope: 'platform',
    intent:
      '계정을 잠그고 세션을 끊습니다. 계정 목록과 보안 이벤트는 볼 수 없습니다.',
    permissions: [
      'account.lock',
      'account.unlock',
      'account.force_logout',
      'audit.read',
    ],
  },
  {
    name: 'SECURITY_ANALYST',
    koName: '보안 분석가',
    scope: 'platform',
    intent:
      '감사 · 보안 이벤트를 보고 의심 세션을 끊습니다. 계정 잠금은 CS 를 거칩니다.',
    permissions: ['audit.read', 'security.event.read', 'account.force_logout'],
  },
  {
    name: 'TENANT_ADMIN',
    koName: '테넌트 위임관리자',
    scope: 'tenant',
    intent:
      '자기 회사의 운영자를 관리하고, 관리자를 한 명 더 세우고, 파트너십을 맺습니다. 도메인 구독은 별도 역할입니다.',
    permissions: [
      'operator.manage',
      'tenant.admin.delegate',
      'partnership.manage',
    ],
  },
  {
    name: 'TENANT_BILLING_ADMIN',
    koName: '테넌트 구독관리자',
    scope: 'tenant',
    intent:
      '자기 회사가 쓸 도메인 구독만 관리합니다. 운영자 관리 권한은 없습니다.',
    permissions: ['subscription.manage'],
  },
];

/** 매트릭스 셀 상태. */
export type AccessLevel = 'full' | 'partial' | 'none';

/**
 * 메뉴 × role 접근 매트릭스 (rbac.md § Seed Matrix 파생).
 *
 * PC-FE-238 에서 3개 → 7개 라이브 표면으로 확장하고, 렌더를 **행=메뉴 / 열=역할**
 * 로 전치했다(역할 6열이 메뉴 7열보다 고정폭에 잘 맞는다). `data-testid` 규약
 * `iam-guide-cell-{role}-{href}` 는 전치와 무관하게 유지된다.
 *
 * `/operator-groups`(스텁, 권한 축 없음) · `/iam`(카드별 부분 게이트) · `/iam/guide`
 * (누구나) 는 권한 축이 없어 매트릭스에서 제외 — 사용법 표에만 나온다.
 */
export interface ScreenAccess {
  /** 화면 라벨. */
  screen: string;
  /** 라우트(참조용 + testid 키). */
  href: string;
  /** 이 화면을 여는 데 필요한 권한 키. */
  gate: string;
  /** role.name → 접근 수준 + 주석. */
  cells: Record<string, { level: AccessLevel; note?: string }>;
}

// 순서는 사이드바 nav(ConsoleSidebarNav.tsx)의 setup-first 순서를 따른다:
// 운영자 관리 → 테넌트 → 권한/권한 세트 → 감사·보안 → 계정 운영 → 구독 → 파트너십.
export const SCREEN_ACCESS: ScreenAccess[] = [
  {
    screen: '운영자 관리',
    href: '/operators',
    gate: 'operator.manage',
    cells: {
      SUPER_ADMIN: { level: 'full' },
      SUPPORT_READONLY: { level: 'none' },
      SUPPORT_LOCK: { level: 'none' },
      SECURITY_ANALYST: { level: 'none' },
      TENANT_ADMIN: { level: 'full', note: '자기 테넌트' },
      TENANT_BILLING_ADMIN: { level: 'none' },
    },
  },
  {
    screen: '테넌트',
    href: '/tenants',
    gate: 'tenant.manage',
    cells: {
      SUPER_ADMIN: { level: 'full' },
      SUPPORT_READONLY: { level: 'none' },
      SUPPORT_LOCK: { level: 'none' },
      SECURITY_ANALYST: { level: 'none' },
      TENANT_ADMIN: { level: 'none' },
      TENANT_BILLING_ADMIN: { level: 'none' },
    },
  },
  {
    screen: '권한 · 권한 세트',
    href: '/permissions',
    gate: 'operator.manage',
    cells: {
      SUPER_ADMIN: { level: 'full' },
      SUPPORT_READONLY: { level: 'none' },
      SUPPORT_LOCK: { level: 'none' },
      SECURITY_ANALYST: { level: 'none' },
      TENANT_ADMIN: { level: 'full' },
      TENANT_BILLING_ADMIN: { level: 'none' },
    },
  },
  {
    screen: '감사 · 보안',
    href: '/audit',
    gate: 'audit.read (+ security.event.read)',
    cells: {
      SUPER_ADMIN: { level: 'full', note: '보안 이벤트 포함' },
      SUPPORT_READONLY: { level: 'full', note: '보안 이벤트 포함' },
      SUPPORT_LOCK: { level: 'partial', note: '보안 이벤트 제외' },
      SECURITY_ANALYST: { level: 'full', note: '보안 이벤트 포함' },
      TENANT_ADMIN: { level: 'none' },
      TENANT_BILLING_ADMIN: { level: 'none' },
    },
  },
  {
    screen: '계정 운영',
    href: '/accounts',
    gate: 'account.read',
    cells: {
      SUPER_ADMIN: { level: 'full' },
      SUPPORT_READONLY: { level: 'full', note: '조회만' },
      // account.lock/unlock 은 있으나 account.read 가 없어 목록 화면은 열 수 없다.
      SUPPORT_LOCK: { level: 'none' },
      SECURITY_ANALYST: { level: 'none' },
      TENANT_ADMIN: { level: 'none' },
      TENANT_BILLING_ADMIN: { level: 'none' },
    },
  },
  {
    screen: '도메인 구독',
    href: '/subscriptions',
    gate: 'subscription.manage',
    cells: {
      SUPER_ADMIN: { level: 'full' },
      SUPPORT_READONLY: { level: 'none' },
      SUPPORT_LOCK: { level: 'none' },
      SECURITY_ANALYST: { level: 'none' },
      TENANT_ADMIN: { level: 'none' },
      TENANT_BILLING_ADMIN: { level: 'full', note: '자기 테넌트' },
    },
  },
  {
    screen: '파트너십',
    href: '/partnerships',
    gate: 'partnership.manage',
    cells: {
      // 의도적 — 파트너십은 두 고객 테넌트 사이 관계이고 플랫폼은 당사자가 아니다.
      SUPER_ADMIN: { level: 'none' },
      SUPPORT_READONLY: { level: 'none' },
      SUPPORT_LOCK: { level: 'none' },
      SECURITY_ANALYST: { level: 'none' },
      TENANT_ADMIN: { level: 'full', note: '자기 테넌트' },
      TENANT_BILLING_ADMIN: { level: 'none' },
    },
  },
];

/**
 * 도메인 롤(별도 축) — 테넌트 직원/협력사가 실제로 받는 role.
 * SoT: auth-service `OperatorRoleDerivation.java`(assume-tenant 파생표).
 */
export const DOMAIN_ROLE_MAP: { domain: string; roles: string }[] = [
  {
    domain: 'wms',
    roles: 'WMS_OPERATOR + 세분(출고 · 입고 · 재고의 읽기/쓰기, 마스터 읽기)',
  },
  { domain: 'ecommerce', roles: 'ECOMMERCE_OPERATOR' },
  { domain: 'scm', roles: 'SCM_OPERATOR' },
  { domain: 'erp', roles: 'ERP_OPERATOR' },
  { domain: 'finance', roles: 'FINANCE_OPERATOR' },
  { domain: 'mes', roles: 'MES_OPERATOR' },
  { domain: 'fan / fan-platform', roles: 'FAN_OPERATOR' },
  {
    domain: '구독하지 않은 도메인',
    roles: '없음 → 게이트웨이가 막습니다',
  },
];
