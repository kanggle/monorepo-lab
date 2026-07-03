/**
 * IAM 개요(가이드) 화면의 정적 참조 데이터 (TASK-PC-FE-163).
 *
 * **SoT**: `projects/iam-platform/specs/services/admin-service/rbac.md`
 * (§ Permission Keys / § Seed Roles / § Seed Matrix). 이 파일은 그 스펙의
 * seed 매트릭스를 화면-표시용으로 옮긴 것이며, admin-service seed(Flyway
 * `V0033__seed_tenant_admin_roles.sql` 외)가 canonical 이다. seed role/권한이
 * 바뀌면 이 파일도 동반 갱신해야 한다(테스트가 role×화면 매트릭스를 단언하여
 * 표면적 드리프트는 잡지만, 권한 키 설명 텍스트는 사람이 맞춰야 함).
 *
 * 이 6개는 **admin-console RBAC 역할**(IAM 콘솔 3화면을 게이트)이다. 테넌트
 * 직원/협력업체가 받는 **도메인 롤**(WMS_OPERATOR·ADMIN·SCM_OPERATOR…,
 * assume-tenant 시 테넌트 구독 도메인에서 파생)은 별도의 축으로, 화면 하단
 * `DOMAIN_ROLE_NOTES` 에서 별도로 설명한다.
 */

/** admin-service 가 평가하는 권한 키 (rbac.md § Permission Keys). */
export interface PermissionKey {
  key: string;
  desc: string;
}

export const PERMISSION_KEYS: PermissionKey[] = [
  { key: 'account.read', desc: '전체 계정 목록 페이지네이션 조회' },
  { key: 'account.lock', desc: '계정 강제 잠금' },
  { key: 'account.unlock', desc: '계정 잠금 해제' },
  { key: 'account.force_logout', desc: '특정 계정의 모든 세션 강제 종료' },
  { key: 'audit.read', desc: '통합 감사 로그 조회' },
  {
    key: 'security.event.read',
    desc: '로그인 이력·의심 이벤트(security-service) 조회',
  },
  {
    key: 'operator.manage',
    desc: '운영자 계정 생성·역할 부여·상태 변경·테넌트 배정',
  },
  {
    key: 'tenant.manage',
    desc: '테넌트 생명주기(조회·생성·상태/표시명) 관리 — 플랫폼 스코프 전용',
  },
  {
    key: 'subscription.manage',
    desc: '테넌트↔도메인 구독(entitlement) 생명주기 관리',
  },
  {
    key: 'tenant.admin.delegate',
    desc: '자기 테넌트 한정 추가 TENANT_ADMIN 임명(in-tenant 위임)',
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
  /** SUPER_ADMIN 처럼 특권 role(플랫폼 `*` 센티넬 보유) 은 시각적으로 구분. */
  elevated?: boolean;
}

export const SEED_ROLES: SeedRole[] = [
  {
    name: 'SUPER_ADMIN',
    koName: '플랫폼 관리자',
    scope: 'platform',
    intent: '전체 권한. 플랫폼 스코프(`*`) — 모든 테넌트 무제약 관리·온보딩.',
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
    intent: 'CS L1. 조회 전용 — 계정·감사·보안이벤트 열람, 변이 불가.',
    permissions: ['account.read', 'audit.read', 'security.event.read'],
  },
  {
    name: 'SUPPORT_LOCK',
    koName: 'CS 상담원 (계정 제어)',
    scope: 'platform',
    intent:
      'CS L2. 계정 제어(잠금/해제/세션종료) + 감사 조회. 보안이벤트·계정목록은 불가.',
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
      '보안팀. 감사·보안이벤트 조회 + 의심 세션 긴급 종료. 계정 lock/unlock 은 CS 경유.',
    permissions: ['audit.read', 'security.event.read', 'account.force_logout'],
  },
  {
    name: 'TENANT_ADMIN',
    koName: '테넌트 위임관리자',
    scope: 'tenant',
    intent:
      '테넌트-scoped 위임관리자(ADR-MONO-024). 자기 테넌트 운영자 관리 + 자기 테넌트 한정 sub-delegation. IAM 평면 전용.',
    permissions: ['operator.manage', 'tenant.admin.delegate'],
  },
  {
    name: 'TENANT_BILLING_ADMIN',
    koName: '테넌트 구독관리자',
    scope: 'tenant',
    intent:
      '테넌트-scoped entitlement 관리자(ADR-MONO-024). 자기 테넌트 도메인 구독 관리. TENANT_ADMIN 과 별도 role.',
    permissions: ['subscription.manage'],
  },
];

/** 매트릭스 셀 상태. */
export type AccessLevel = 'full' | 'partial' | 'none';

/** IAM 콘솔 3화면 × role 접근 매트릭스 (rbac.md § Seed Matrix 파생). */
export interface ScreenAccess {
  /** 화면 라벨. */
  screen: string;
  /** 라우트(참조용). */
  href: string;
  /** 이 화면을 여는 데 필요한 권한 키. */
  gate: string;
  /** role.name → 접근 수준 + 주석. */
  cells: Record<string, { level: AccessLevel; note?: string }>;
}

// 화면 순서는 사이드바 IAM nav(ConsoleSidebarNav.tsx)의 setup-first 순서와
// 일치시킨다: 운영자 관리 → 계정 운영 → 감사·보안. (인덱스 단언 테스트
// IamGuideScreen.test.tsx '스팟 체크'도 이 순서를 전제한다.)
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
      TENANT_ADMIN: { level: 'full', note: '자기 테넌트 한정' },
      TENANT_BILLING_ADMIN: { level: 'none' },
    },
  },
  {
    screen: '계정 운영',
    href: '/accounts',
    gate: 'account.read',
    cells: {
      SUPER_ADMIN: { level: 'full' },
      SUPPORT_READONLY: { level: 'full' },
      // account.lock/unlock 은 있으나 account.read 가 없어 목록 화면은 열 수 없다.
      SUPPORT_LOCK: { level: 'none' },
      SECURITY_ANALYST: { level: 'none' },
      TENANT_ADMIN: { level: 'none' },
      TENANT_BILLING_ADMIN: { level: 'none' },
    },
  },
  {
    screen: '감사 · 보안',
    href: '/audit',
    gate: 'audit.read (+ security.event.read)',
    cells: {
      SUPER_ADMIN: { level: 'full', note: '보안이벤트 포함' },
      SUPPORT_READONLY: { level: 'full', note: '보안이벤트 포함' },
      SUPPORT_LOCK: { level: 'partial', note: '기본만(보안이벤트 불가)' },
      SECURITY_ANALYST: { level: 'full', note: '보안이벤트 포함' },
      TENANT_ADMIN: { level: 'none' },
      TENANT_BILLING_ADMIN: { level: 'none' },
    },
  },
];

/** 운영 시 롤 부여 위임 체인(운영자 관리 화면에서 실제로 클릭되는 흐름). */
export interface DelegationStep {
  actor: string;
  action: string;
}

export const DELEGATION_CHAIN: DelegationStep[] = [
  {
    actor: 'SUPER_ADMIN (플랫폼 운영자)',
    action:
      'a회사용 TENANT_ADMIN 운영자를 생성하고 TENANT_ADMIN role 을 부여(운영자 관리 → 운영자 생성/역할 변경). 플랫폼 스코프라 모든 role·테넌트 무제약.',
  },
  {
    actor: 'a회사 TENANT_ADMIN',
    action:
      '자기 테넌트 직원 운영자를 생성하고 도메인 접근을 위해 테넌트에 배정(배정/배정 해제). 필요 시 추가 TENANT_ADMIN 을 위임(tenant.admin.delegate). 모두 자기 테넌트 한정.',
  },
  {
    actor: 'a회사 직원 / 협력업체',
    action:
      '로그인 후 테넌트 선택(assume-tenant) 시, 배정된 테넌트의 구독 도메인에서 도메인 롤(WMS_OPERATOR·ADMIN…)이 자동 파생되어 도메인 서비스를 사용.',
  },
];

/** 위임 시 발효하는 구조적 가드(ADR-MONO-024). */
export const DELEGATION_GUARDS: { name: string; desc: string }[] = [
  {
    name: 'TenantScopeGuard (D2 confinement)',
    desc: '비-플랫폼 role 은 grant row 의 tenant_id 로 관리 대상 테넌트가 한정된다 — 타 테넌트 대상 변이는 403 TENANT_SCOPE_DENIED. SUPER_ADMIN(`*`)은 net-zero(무제약).',
  },
  {
    name: 'RoleGrantGuard (D3 no-escalation)',
    desc: '≤-own 규칙 — 자신이 보유하지 않은 권한을 부여할 수 없고 SUPER_ADMIN 은 부여 불가(403 ROLE_GRANT_FORBIDDEN). TENANT_ADMIN 은 tenant.admin.delegate 보유 시에만 TENANT_ADMIN 을 재위임할 수 있다.',
  },
];

/**
 * 도메인 롤(별도 축) 설명 — 테넌트 직원/협력업체가 실제로 받는 role.
 * SoT: auth-service `OperatorRoleDerivation.java`(assume-tenant 파생표).
 */
export const DOMAIN_ROLE_MAP: { domain: string; roles: string }[] = [
  { domain: 'wms', roles: 'WMS_OPERATOR + 세분(OUTBOUND/INBOUND/INVENTORY READ·WRITE, MASTER_READ)' },
  { domain: 'ecommerce', roles: 'ADMIN' },
  { domain: 'scm', roles: 'SCM_OPERATOR' },
  { domain: 'erp', roles: 'ERP_OPERATOR' },
  { domain: 'finance', roles: 'FINANCE_OPERATOR' },
  { domain: 'mes', roles: 'MES_OPERATOR' },
  { domain: 'fan / fan-platform', roles: 'FAN_OPERATOR' },
  { domain: 'gap / 미지', roles: '(없음 → 게이트웨이 403)' },
];
