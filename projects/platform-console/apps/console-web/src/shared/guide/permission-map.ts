/**
 * TASK-PC-FE-298 — 권한·기능 매핑 표 (콘솔 nav 항목 전부).
 *
 * =============================================================================
 * 이 파일이 손으로 적는 것과 **파생하는 것**
 * =============================================================================
 * 표의 열 중 다음은 여기 적지 않고 **다른 원장에서 파생**한다(적으면 두 사본이 갈라진다):
 *
 *   - 메뉴명 · 메뉴 뎁스 · 상위 메뉴 → `console-nav-config.ts` 의 `GROUPS`
 *     (`resolvePermissionMap()` 이 href 로 조인).
 *   - 비로그인 데모 가능 여부 → `shared/sample/coverage.ts` 의 `screenStatusFor()`
 *     (ADR-MONO-074 샘플 원장 — `static` · `ready` · `pending`).
 *   - 테스트 계정 접근 가능 여부 → 아래 `RBAC_SEED_MATRIX`(rbac.md 사본) ×
 *     `DEMO_TEST_ACCOUNT`(데모 시드) 로 계산(`testAccountAccess()`).
 *
 * 손으로 적는 열(권한 코드 · 설명 · 조회/생성/수정/삭제 · 용도 · 연결 서비스)은 행마다
 * `sources` 에 **어느 파일의 어느 줄에서 왔는지**를 남긴다(AC-4). 추측으로 채운 칸은 없다 —
 * 근거가 없는 칸은 `정보 없음` 으로 둔다.
 *
 *   - IAM 행의 권한 코드 = admin-service 컨트롤러의 `@RequiresPermission`
 *     (`projects/iam-platform/apps/admin-service/src/main/java/com/example/admin/presentation/*Controller.java`)
 *     와 `rbac.md` § Permission Keys(:60-74).
 *   - 도메인 행의 권한 = assume-tenant 때 파생되는 도메인 롤
 *     (`auth-service` `OperatorRoleDerivation.java:53-58,102-107`) — IAM RBAC 과 **다른 평면**이다
 *     (ADR-MONO-035, `iam-guide/data.ts` `AUTH_PLANES`).
 *   - 조회/생성/수정/삭제 = 그 화면이 쓰는 콘솔 프록시 라우트가 export 하는 HTTP 메서드
 *     (`src/app/api/**\/route.ts`) + 그 화면 `page.tsx` 의 헤더 주석(READ-ONLY 선언 등).
 *
 * =============================================================================
 * 🔴 한계 — 드리프트 테스트가 잡는 것과 못 잡는 것
 * =============================================================================
 * `tests/unit/permission-map-drift.test.ts` 는 (a) nav href 가 이 표에 행이 없거나
 * (b) 이 표의 행이 nav 에 없거나 (c) IAM 행의 권한 키가 `RBAC_SEED_MATRIX` 에 없으면 빨개진다.
 * **권한 코드 자체의 최신성은 자동으로 못 잡는다** — `rbac.md` 나 컨트롤러 애노테이션이
 * 바뀌어도 이 표는 조용히 옛 값을 말한다. 그 둘을 바꾸는 PR 은 이 파일도 함께 고쳐야 한다.
 */
import {
  GROUPS,
  isParent,
  type NavLeaf,
} from '@/shared/ui/console-nav-config';
import { screenStatusFor, type ScreenStatus } from '@/shared/sample/coverage';

/* ─────────────────────────── RBAC 원장 사본 ─────────────────────────── */

export const RBAC_ROLES = [
  'SUPER_ADMIN',
  'SUPPORT_READONLY',
  'SUPPORT_LOCK',
  'SECURITY_ANALYST',
  'TENANT_ADMIN',
  'TENANT_BILLING_ADMIN',
  'ORG_ADMIN',
] as const;
export type RbacRole = (typeof RBAC_ROLES)[number];

/**
 * `rbac.md` § Seed Matrix (role × permission) 를 **그대로** 옮긴 것.
 * 열 순서 = `RBAC_ROLES`. ✅=true / ❌=false.
 */
export const RBAC_SEED_MATRIX_SOURCE =
  'projects/iam-platform/specs/services/admin-service/rbac.md:98-114 (§ Seed Matrix)';

const row = (...cells: (0 | 1)[]): Record<RbacRole, boolean> =>
  Object.fromEntries(RBAC_ROLES.map((r, i) => [r, cells[i] === 1])) as Record<
    RbacRole,
    boolean
  >;

export const RBAC_SEED_MATRIX: Readonly<Record<string, Record<RbacRole, boolean>>> = {
  //                          SA SR SL AN TA TB OA
  'account.read':          row(1, 1, 0, 0, 0, 0, 0),
  'account.lock':          row(1, 0, 1, 0, 0, 0, 0),
  'account.unlock':        row(1, 0, 1, 0, 0, 0, 0),
  'account.force_logout':  row(1, 0, 1, 1, 0, 0, 0),
  'audit.read':            row(1, 1, 1, 1, 0, 0, 0),
  'security.event.read':   row(1, 1, 0, 1, 0, 0, 0),
  'operator.manage':       row(1, 0, 0, 0, 1, 0, 1),
  'tenant.manage':         row(1, 0, 0, 0, 0, 0, 0),
  'subscription.manage':   row(1, 0, 0, 0, 0, 1, 0),
  'tenant.admin.delegate': row(0, 0, 0, 0, 1, 0, 1),
  'partnership.manage':    row(0, 0, 0, 0, 1, 0, 0),
  'org.manage':            row(1, 0, 0, 0, 0, 0, 1),
  'group.manage':          row(1, 0, 0, 0, 1, 0, 1),
};

/* ─────────────────────────── 데모 테스트 계정 ─────────────────────────── */

export type DomainKey = 'wms' | 'scm' | 'finance' | 'erp' | 'ecommerce';

/**
 * 데모 테스트 계정(`demo-operator`)이 **실제로** 가진 것. 로그인 이메일 문자열은 여기
 * 적지 않는다 — 그 유일본은 `widgets/demo-credentials/DemoLoginCredentials.tsx` 이고
 * (`verify-demo-wrapper.sh` z11 이 사본 셋을 대조), 화면은 그 상수를 props 로 받는다.
 */
export const DEMO_TEST_ACCOUNT = {
  operatorId: 'demo-operator',
  /** admin-service `admin_operator_roles` 에 묶인 RBAC 역할. */
  adminRoles: ['SUPER_ADMIN'] as RbacRole[],
  /** assume-tenant 가능한 테넌트(`operator_tenant_assignment`). */
  assumableTenants: ['demo-corp', 'ecommerce'],
  /** demo-corp 의 ACTIVE 구독 → assume 시 파생되는 도메인. */
  entitledDomains: ['ecommerce', 'wms', 'scm', 'erp', 'finance'] as DomainKey[],
  sources: [
    'projects/iam-platform/apps/admin-service/src/main/resources/db/migration-dev/R__seed_demo_operator.sql §1 operator · §2 SUPER_ADMIN 바인딩 · §3 demo-corp · §4 ecommerce 배정',
    'projects/iam-platform/apps/account-service/src/main/resources/db/migration-dev/R__05_seed_demo_corp_tenant_and_consumer_accounts.sql:81-92 (demo-corp 5개 도메인 ACTIVE 구독)',
    'infra/demo/seed/README.md:47-55 (demo-corp assume → 5개 도메인 운영 롤)',
  ],
} as const;

/* ─────────────────────────── 행 타입 ─────────────────────────── */

export type PermissionGate =
  /** 정적 화면 — 백엔드 호출 없음, 누구나. */
  | { kind: 'public' }
  /** 로그인한 운영자면 누구나(권한 키 없음). */
  | { kind: 'operator'; note: string }
  /** admin-service `@RequiresPermission`(또는 동등한 인라인 검사). */
  | { kind: 'admin'; permission: string; extra?: string }
  /** 카드마다 다른 키로 부분 게이트되는 개요. */
  | { kind: 'admin-per-card'; permissions: string[] }
  /** 도메인 롤 — assume-tenant 시 구독 도메인에서 파생. */
  | { kind: 'domain'; domain: DomainKey; roles: string[]; extra?: string };

export interface Crud {
  read: boolean;
  create: boolean;
  update: boolean;
  delete: boolean;
}

export type MapArea =
  | 'platform'
  | 'iam'
  | 'customer-identity'
  | 'org'
  | DomainKey;

export interface PermissionMapRow {
  href: string;
  area: MapArea;
  gate: PermissionGate;
  /** 이 화면이 보여주는 것 — 한 줄. */
  description: string;
  crud: Crud;
  /** 조회/생성/수정/삭제 외의 조작이나 제약(선택). */
  crudNote?: string;
  /** 누가 왜 쓰는가 — 한 줄. */
  purpose: string;
  /** 이 화면이 부르는 백엔드(게이트웨이 경유 서비스). 없으면 빈 배열. */
  services: string[];
  /** 파생 근거 — `파일:줄` 형식. */
  sources: string[];
  /** 알려진 불일치·특수 케이스(AC-5 등). */
  mismatch?: string;
}

const R: Crud = { read: true, create: false, update: false, delete: false };
const crud = (c: string): Crud => ({
  read: c.includes('R'),
  create: c.includes('C'),
  update: c.includes('U'),
  delete: c.includes('D'),
});

const ADMIN_CTRL =
  'projects/iam-platform/apps/admin-service/src/main/java/com/example/admin/presentation';
const API = 'projects/platform-console/apps/console-web/src/app/api';
const PAGES = 'projects/platform-console/apps/console-web/src/app/(console)';
const RBAC = 'projects/iam-platform/specs/services/admin-service/rbac.md';
const ROLE_DERIVATION =
  'projects/iam-platform/apps/auth-service/src/main/java/com/example/auth/infrastructure/oauth2/OperatorRoleDerivation.java';

const WMS_ROLES = [
  'WMS_OPERATOR',
  'OUTBOUND_READ',
  'OUTBOUND_WRITE',
  'INBOUND_READ',
  'INBOUND_WRITE',
  'INVENTORY_READ',
  'INVENTORY_WRITE',
  'MASTER_READ',
];
const WMS_GW = 'wms gateway-service';
const WMS_GW_ROUTES =
  'projects/wms-platform/apps/gateway-service/src/main/resources/application.yml:58-112 (master/inbound/inventory/outbound/admin 라우트)';

const guideRow = (href: string, area: MapArea, what: string): PermissionMapRow => ({
  href,
  area,
  gate: { kind: 'public' },
  description: `${what} 정적 가이드 — 개념·용어·메뉴·권한·흐름·연결 서비스.`,
  crud: R,
  purpose: '처음 온 사람(샘플 방문자 포함)이 화면을 읽기 전에 방향을 잡는다.',
  services: [],
  sources: [`${PAGES}${href}/page.tsx`, 'projects/platform-console/apps/console-web/src/shared/sample/coverage.ts:188-195 (static)'],
});

/* ─────────────────────────── 표 본문 ─────────────────────────── */

export const PERMISSION_MAP: readonly PermissionMapRow[] = [
  // ── 최상위 ────────────────────────────────────────────────────────────
  {
    href: '/guide',
    area: 'platform',
    gate: { kind: 'public' },
    description: '콘솔 전체 가이드 — 아키텍처·서비스·서버 구성·전체 메뉴·권한·업무 흐름·기동/종료.',
    crud: R,
    purpose: '콘솔을 처음 보는 사람이 전체 그림부터 잡는다.',
    services: [],
    sources: [`${PAGES}/guide/page.tsx`, 'projects/platform-console/apps/console-web/src/features/global-guide/data.ts'],
  },
  {
    href: '/dashboards/overview',
    area: 'platform',
    gate: {
      kind: 'operator',
      note: '셸 진입(로그인)만 요구. 카드별 도메인 데이터는 각 도메인 게이트가 따로 판정.',
    },
    description: '5개 도메인 요약 카드 + 도메인 상태 요약(→ 도메인 상태 화면).',
    crud: R,
    purpose: '운영자가 로그인 직후 전체 상태를 한눈에 본다.',
    services: ['console-bff (operator-overview · domain-health)'],
    sources: [
      `${API}/console/dashboards/operator-overview/route.ts (GET)`,
      `${API}/console/dashboards/domain-health/route.ts (GET)`,
    ],
  },
  {
    href: '/console',
    area: 'platform',
    gate: {
      kind: 'operator',
      note: 'GET /api/admin/console/registry 에는 @RequiresPermission 이 없다 — 유효한 운영자 토큰이면 누구나(자기 테넌트 범위로 축소).',
    },
    description: '제품(도메인) 카탈로그 타일 — 레지스트리에서 데이터 기반으로 렌더.',
    crud: R,
    purpose: '어떤 도메인을 쓸 수 있는지 보고 그 화면으로 이동한다.',
    services: ['iam admin-service (console registry)'],
    sources: [`${ADMIN_CTRL}/console/ConsoleRegistryController.java:21-31,42`, `${API}/registry/route.ts (GET)`],
  },

  // ── 관리 ▸ IAM ────────────────────────────────────────────────────────
  guideRow('/iam/guide', 'iam', 'IAM'),
  {
    href: '/iam',
    area: 'iam',
    gate: { kind: 'admin-per-card', permissions: ['operator.manage', 'account.read', 'audit.read'] },
    description: '운영자 · 계정 · 감사 현황 카드(IAM 목록 엔드포인트 직접 fan-out).',
    crud: R,
    purpose: 'IAM 도메인 상태를 한 화면에서 요약해 본다.',
    services: ['iam admin-service'],
    sources: [
      'projects/platform-console/apps/console-web/src/features/iam-overview/api/overview-state.ts:28-30',
    ],
  },
  {
    href: '/operators',
    area: 'iam',
    gate: { kind: 'admin', permission: 'operator.manage' },
    description: '운영자 목록 · 등록 · 역할 변경 · 상태 변경 · 테넌트 배정/해제 · 조직 범위.',
    crud: crud('CRUD'),
    crudNote: '삭제 = 테넌트 배정 해제(DELETE assignment). 운영자 자체는 삭제가 아니라 상태 변경(사용중지).',
    purpose: '운영자(워크포스 신원)를 만들고 권한·범위를 정한다.',
    services: ['iam admin-service'],
    sources: [
      `${ADMIN_CTRL}/OperatorAdminController.java:93,139,171,190,234`,
      `${ADMIN_CTRL}/OperatorOrgScopeController.java:59,74,102,118`,
      `${API}/operators/**/route.ts (GET,POST,PUT,DELETE)`,
    ],
  },
  {
    href: '/operator-groups',
    area: 'iam',
    gate: { kind: 'admin', permission: 'group.manage' },
    description: '운영자 그룹 CRUD · 멤버 · 그룹 grant(fan-out).',
    crud: crud('CRUD'),
    purpose: '여러 운영자에게 역할·테넌트 배정을 한 번에 부여한다(ADR-MONO-046).',
    services: ['iam admin-service'],
    sources: [`${ADMIN_CTRL}/GroupAdminController.java:58-170`, `${API}/groups/**/route.ts`],
  },
  {
    href: '/org-hierarchy',
    area: 'iam',
    gate: { kind: 'admin', permission: 'org.manage' },
    description: '조직 노드 트리 · 엔타이틀먼트 상한(ceiling) · ORG_ADMIN 배정 · 소속 테넌트.',
    crud: crud('CRUD'),
    purpose: '회사(조직 노드) 단위로 테넌트를 묶고 상한을 건다(ADR-MONO-047).',
    services: ['iam admin-service → account-service (트리 소유)'],
    sources: [`${ADMIN_CTRL}/OrgNodeAdminController.java:54-149`, `${RBAC}:73 (org.manage)`],
  },
  {
    href: '/tenants',
    area: 'iam',
    gate: { kind: 'admin', permission: 'tenant.manage' },
    description: '테넌트 목록 · 상세 · 생성 · 표시명/상태 변경.',
    crud: crud('CRU'),
    crudNote: '삭제 엔드포인트 없음(상태 변경으로 대신).',
    purpose: '격리 경계(테넌트) 생명주기를 관리한다 — 조회에도 같은 키가 필요해 사실상 SUPER_ADMIN 전용.',
    services: ['iam admin-service'],
    sources: [`${ADMIN_CTRL}/tenant/TenantAdminController.java:75,96,120,143`, `${RBAC}:69`],
  },
  {
    href: '/permissions',
    area: 'iam',
    gate: { kind: 'admin', permission: 'operator.manage' },
    description: '권한 키 카탈로그 — 역할을 펼치면 보유 키.',
    crud: R,
    purpose: '어떤 권한 키가 있고 어느 역할이 갖는지 찾아본다.',
    services: ['iam admin-service'],
    sources: [`${ADMIN_CTRL}/RoleAdminController.java:57,74`, `${RBAC}:68,80 (TASK-BE-486)`],
    mismatch:
      '읽기 전용 화면인데 `operator.manage`(관리 키)로 게이트된다 — 전용 읽기 키 없이 기존 키를 재사용한 결정(rbac.md:80, TASK-BE-486). 그래서 조회 전용 역할(SUPPORT_READONLY)은 권한 카탈로그를 볼 수 없고, 운영자를 관리할 수 있는 역할만 볼 수 있다.',
  },
  {
    href: '/permission-sets',
    area: 'iam',
    gate: { kind: 'admin', permission: 'operator.manage' },
    description: '권한 세트(= 역할) — 세트를 펼치면 포함된 권한 키.',
    crud: R,
    purpose: '테넌트 배정에 붙일 수 있는 권한 묶음을 본다.',
    services: ['iam admin-service'],
    sources: [`${ADMIN_CTRL}/RoleAdminController.java:57`, `${RBAC}:80`],
    mismatch:
      '「권한」과 같은 특수 케이스 — 읽기 전용이지만 `operator.manage` 게이트(rbac.md:80). 별도 엔드포인트 없이 `GET /api/admin/roles` 를 권한 세트 관점으로 다시 보여준다.',
  },
  {
    href: '/audit',
    area: 'iam',
    gate: {
      kind: 'admin',
      permission: 'audit.read',
      extra: '로그인 이력·의심 활동 출처는 security.event.read 추가 필요',
    },
    description: '감사 로그 · 로그인 이력 · 의심 활동 조회(필터).',
    crud: R,
    purpose: '누가 언제 무엇을 했는지 추적한다.',
    services: ['iam admin-service → security-service'],
    sources: [`${ADMIN_CTRL}/AuditController.java:50`, `${RBAC}:66-67`],
  },

  // ── 고객 신원 ─────────────────────────────────────────────────────────
  {
    href: '/accounts',
    area: 'customer-identity',
    gate: {
      kind: 'admin',
      permission: 'account.read',
      extra: '잠금/일괄잠금/GDPR 삭제=account.lock · 해제=account.unlock · 세션 종료=account.force_logout · 내보내기=audit.read',
    },
    description: '소비자 계정 검색 · 잠금/해제 · 일괄 잠금 · 세션 강제 종료 · 데이터 내보내기 · GDPR 삭제.',
    crud: crud('RUD'),
    crudNote: '생성 없음. 삭제 = GDPR 삭제(되돌릴 수 없음).',
    purpose: '서비스 전체가 공유하는 소비자 계정을 지원·보안 목적으로 제어한다.',
    services: ['iam admin-service → account-service · auth-service(세션)'],
    sources: [
      `${ADMIN_CTRL}/AccountAdminController.java:72 (GET — 인라인 account.read 검사, 애노테이션 아님), 140,164,185`,
      `${ADMIN_CTRL}/AdminGdprController.java:31,56`,
      `${ADMIN_CTRL}/SessionAdminController.java:32`,
    ],
    mismatch:
      '목록 GET 은 `@RequiresPermission` 애노테이션이 아니라 메서드 안의 인라인 검사다(이메일 분기가 무권한이라 일괄 애노테이션 불가). 내보내기는 audit.read, GDPR 삭제는 account.lock 으로 게이트돼 이름과 직관이 어긋난다.',
  },

  // ── 조직 설정 ─────────────────────────────────────────────────────────
  {
    href: '/subscriptions',
    area: 'org',
    gate: { kind: 'admin', permission: 'subscription.manage' },
    description: '내 테넌트의 도메인 구독 켜기 · 일시중지 · 재개 · 해지.',
    crud: crud('RCU'),
    crudNote: '목록 GET 엔드포인트가 없다 — 현재 상태는 레지스트리(카탈로그)에서 파생한다. 해지는 상태 전이(CANCELLED).',
    purpose: '테넌트 주인이 쓸 도메인을 스스로 켠다 — 구독 도메인이 곧 직원들의 도메인 운영 권한이 된다.',
    services: ['iam admin-service → account-service'],
    sources: [
      `${ADMIN_CTRL}/SubscriptionAdminController.java:41,57`,
      `${PAGES}/subscriptions/page.tsx (헤더 — no list/GET subscriptions endpoint)`,
    ],
  },
  {
    href: '/partnerships',
    area: 'org',
    gate: { kind: 'admin', permission: 'partnership.manage' },
    description: '회사 간 파트너십 초대 · 수락 · 일시중지 · 재개 · 종료 · 참여 운영자 배정/해제.',
    crud: crud('CRUD'),
    crudNote: '삭제 = 참여 운영자 해제. 파트너십 자체는 종료(상태 전이).',
    purpose: '다른 회사에 우리 테넌트 운영 일부를 위임하거나 위임받는다(ADR-MONO-045).',
    services: ['iam admin-service'],
    sources: [`${ADMIN_CTRL}/PartnershipAdminController.java:50-158`, `${RBAC}:72,112`],
    mismatch:
      'SUPER_ADMIN 도 이 키가 없다(rbac.md:112) — 파트너십은 두 고객 테넌트 사이의 관계이고 플랫폼은 당사자가 아니라는 설계. 그래서 SUPER_ADMIN 인 데모 계정은 이 화면에서 403 을 받는다.',
  },

  // ── WMS ───────────────────────────────────────────────────────────────
  guideRow('/wms/guide', 'wms', 'WMS'),
  {
    href: '/wms',
    area: 'wms',
    gate: { kind: 'domain', domain: 'wms', roles: WMS_ROLES },
    description: '재고·처리량·주문·알림 요약(읽기 모델 대시보드).',
    crud: crud('RU'),
    crudNote: '수정 = 운영 알림 확인(acknowledge).',
    purpose: '창고 상태를 요약해 보고 알림을 처리한다.',
    services: [`${WMS_GW} → admin-service (/api/v1/admin/** 읽기 모델)`],
    sources: [
      'projects/platform-console/apps/console-web/src/features/wms-ops/api/wms-inventory-api.ts:15-88',
      `${API}/wms/alerts/[alertId]/acknowledge/route.ts (POST)`,
      WMS_GW_ROUTES,
      `${ROLE_DERIVATION}:53-58,102`,
    ],
  },
  {
    href: '/wms/inbound',
    area: 'wms',
    gate: { kind: 'domain', domain: 'wms', roles: WMS_ROLES },
    description: 'ASN(입고예정) 목록 + 검수 결과 조회.',
    crud: R,
    purpose: '들어올 물건과 검수 결과를 확인한다.',
    services: [`${WMS_GW} → admin-service (/dashboard/asns)`],
    sources: [`${PAGES}/wms/inbound/page.tsx (READ-ONLY)`, `${API}/wms/inbound/asns/route.ts (GET)`],
  },
  {
    href: '/wms/inventory',
    area: 'wms',
    gate: { kind: 'domain', domain: 'wms', roles: WMS_ROLES },
    description: '위치·SKU·로트별 재고 수량 버킷(가용/예약/손상) 조회.',
    crud: R,
    purpose: '지금 창고에 무엇이 얼마나 있는지 본다.',
    services: [`${WMS_GW} → admin-service (/dashboard/inventory)`],
    sources: [`${API}/wms/inventory/route.ts (GET)`, `${API}/wms/inventory/by-key/route.ts (GET)`],
  },
  {
    href: '/wms/outbound',
    area: 'wms',
    gate: {
      kind: 'domain',
      domain: 'wms',
      roles: WMS_ROLES,
      extra: '주문 취소는 OUTBOUND_ADMIN(wms-guide WMS_ROLES) — assume-tenant 파생 롤은 ADMIN-tier 를 제외한다(OperatorRoleDerivation.java:28-33)',
    },
    description: '출고 주문 · 피킹/패킹/출고 확정 · 택배(출고) · 운송사 통보 재시도.',
    crud: crud('RU'),
    crudNote: '수정 = 피킹·패킹·출고 확정·취소·발송 재시도(상태 전이).',
    purpose: '주문을 피킹→패킹→출고까지 진행시킨다.',
    services: [
      `${WMS_GW} → outbound-service (/api/v1/outbound/**) · admin-service (/dashboard/shipments)`,
      'scm logistics-service (/api/v1/logistics — 운송사 통보)',
    ],
    sources: [
      'projects/platform-console/apps/console-web/src/features/wms-outbound-ops/api/outbound-client.ts:35-43',
      'projects/platform-console/apps/console-web/src/features/wms-outbound-ops/api/outbound-logistics-api.ts:88,116',
      `${API}/wms/outbound/[orderId]/{pick,pack,ship,cancel,retry-dispatch}/route.ts (POST)`,
      'projects/platform-console/apps/console-web/src/features/wms-guide/data.ts:299-335 (WMS_ROLES)',
    ],
  },
  {
    href: '/wms/master',
    area: 'wms',
    gate: { kind: 'domain', domain: 'wms', roles: WMS_ROLES },
    description: '창고/구역/로케이션/SKU/Lot/거래처 참조 데이터 조회.',
    crud: R,
    crudNote: '마스터 쓰기(MASTER_WRITE)는 파생 롤에 의도적으로 없다.',
    purpose: '물류가 참조하는 기준 데이터를 확인한다.',
    services: [`${WMS_GW} → admin-service (/dashboard/refs/{type})`],
    sources: [`${PAGES}/wms/master/page.tsx (READ-ONLY)`, `${ROLE_DERIVATION}:35-49`],
  },
  {
    href: '/wms/operations',
    area: 'wms',
    gate: { kind: 'domain', domain: 'wms', roles: WMS_ROLES },
    description: '운영 설정(예약 TTL·저재고 임계치 등) + 읽기 모델 프로젝션 상태 조회.',
    crud: R,
    crudNote: '설정 변경(WRITE)은 이 화면의 범위 밖(page.tsx 헤더).',
    purpose: '창고 운영 파라미터와 읽기 모델 지연을 확인한다.',
    services: [`${WMS_GW} → admin-service (/settings · /operations/projection-status)`],
    sources: [`${PAGES}/wms/operations/page.tsx:37-39 (READ-ONLY)`, `${API}/wms/settings/route.ts (GET)`],
  },

  // ── SCM ───────────────────────────────────────────────────────────────
  guideRow('/scm/guide', 'scm', 'SCM'),
  {
    href: '/scm',
    area: 'scm',
    gate: { kind: 'domain', domain: 'scm', roles: ['SCM_OPERATOR'] },
    description: '조달·재고 가시성 요약 밴드.',
    crud: R,
    purpose: '공급망 상태를 요약해 본다.',
    services: ['scm gateway-service → procurement-service · inventory-visibility-service'],
    sources: [`${ROLE_DERIVATION}:104`, 'projects/platform-console/apps/console-web/src/features/scm-guide/data.ts:60-90'],
  },
  {
    href: '/scm/procurement',
    area: 'scm',
    gate: { kind: 'domain', domain: 'scm', roles: ['SCM_OPERATOR'] },
    description: '발주(PO) 목록 · 상세 조회.',
    crud: R,
    purpose: '발주가 어느 상태인지 확인한다.',
    services: ['scm gateway-service → procurement-service (/api/v1/procurement)'],
    sources: [`${PAGES}/scm/procurement/page.tsx (READ-ONLY)`, `${API}/scm/po/route.ts (GET)`],
  },
  {
    href: '/scm/inventory',
    area: 'scm',
    gate: { kind: 'domain', domain: 'scm', roles: ['SCM_OPERATOR'] },
    description: '재고 가시성 스냅샷 · SKU · 노드 · 신선도 조회.',
    crud: R,
    purpose: '여러 노드의 재고를 한곳에서 본다.',
    services: ['scm gateway-service → inventory-visibility-service (/api/v1/inventory-visibility)'],
    sources: [`${PAGES}/scm/inventory/page.tsx (READ-ONLY)`, `${API}/scm/snapshot/route.ts (GET)`],
  },
  {
    href: '/scm/replenishment',
    area: 'scm',
    gate: { kind: 'domain', domain: 'scm', roles: ['SCM_OPERATOR'] },
    description: '보충 추천 목록 · 승인 · 기각.',
    crud: crud('RU'),
    crudNote: '수정 = 추천 승인/기각(승인 시 DRAFT 발주가 생긴다).',
    purpose: '재주문 추천을 검토해 발주로 이어 준다.',
    services: ['scm gateway-service → demand-planning-service (/api/v1/demand-planning)'],
    sources: [`${API}/scm/demand-planning/suggestions/[id]/{approve,dismiss}/route.ts (POST)`],
  },
  {
    href: '/scm/config',
    area: 'scm',
    gate: { kind: 'domain', domain: 'scm', roles: ['SCM_OPERATOR'] },
    description: 'SKU별 재주문 정책 · SKU→공급사 매핑 조회/업서트.',
    crud: crud('RCU'),
    crudNote: '생성/수정 = PUT 업서트(같은 호출).',
    purpose: '보충 계획이 참조하는 설정을 채운다(SKU_SUPPLIER_UNMAPPED 해결 경로).',
    services: ['scm gateway-service → demand-planning-service'],
    sources: [
      `${API}/scm/demand-planning/policies/[skuCode]/route.ts (GET,PUT)`,
      `${API}/scm/demand-planning/sku-supplier-map/[skuCode]/route.ts (GET,PUT)`,
    ],
  },

  // ── Finance ───────────────────────────────────────────────────────────
  guideRow('/finance/guide', 'finance', 'Finance'),
  {
    href: '/finance',
    area: 'finance',
    gate: { kind: 'domain', domain: 'finance', roles: ['FINANCE_OPERATOR'] },
    description: '원장 집계 타일 + 운영자 본인 기본 계좌 단건 스냅샷.',
    crud: R,
    purpose: '재무 상태를 요약해 본다(계좌 목록 집계는 하지 않는다).',
    services: ['finance account-service · ledger-service'],
    sources: ['projects/platform-console/apps/console-web/src/features/finance-guide/data.ts:58-120', `${ROLE_DERIVATION}:106`],
  },
  {
    href: '/finance/accounts',
    area: 'finance',
    gate: { kind: 'domain', domain: 'finance', roles: ['FINANCE_OPERATOR'] },
    description: 'accountId 로 계좌 상세 · 통화별 잔액 · 거래 이력 조회.',
    crud: R,
    purpose: '특정 계좌의 상태와 거래를 확인한다.',
    services: ['finance account-service'],
    sources: [`${PAGES}/finance/accounts/page.tsx (READ-ONLY)`, `${API}/finance/accounts/[accountId]/route.ts (GET)`],
  },
  {
    href: '/ledger',
    area: 'finance',
    gate: { kind: 'domain', domain: 'finance', roles: ['FINANCE_OPERATOR'] },
    description: '시산표 · 회계 기간 · 분개 · 계정 드릴 · 대사 큐 · FX 환율.',
    crud: crud('RU'),
    crudNote: '수정 = 대사 차이 해소(resolve) · FX 환율 새로고침.',
    purpose: '복식부기 원장의 균형과 대사 차이를 관리한다.',
    services: ['finance ledger-service'],
    sources: [
      `${API}/ledger/reconciliation/discrepancies/[id]/resolve/route.ts (POST)`,
      `${API}/ledger/fx-rates/refresh/route.ts (POST)`,
    ],
  },

  // ── ERP ───────────────────────────────────────────────────────────────
  guideRow('/erp/guide', 'erp', 'ERP'),
  {
    href: '/erp',
    area: 'erp',
    gate: { kind: 'domain', domain: 'erp', roles: ['ERP_OPERATOR'] },
    description: '마스터 5종 카운트 + 결재 대기 + 활성 위임 타일.',
    crud: R,
    purpose: 'ERP 상태를 요약해 본다.',
    services: ['erp gateway-service → masterdata-service · approval-service'],
    sources: ['projects/platform-console/apps/console-web/src/features/erp-guide/data.ts:65-95', `${ROLE_DERIVATION}:105`],
  },
  {
    href: '/erp/masters',
    area: 'erp',
    gate: { kind: 'domain', domain: 'erp', roles: ['ERP_OPERATOR'] },
    description: '부서·직원·직급·비용센터·거래처 마스터 조회 · 등록 · 수정 · 폐기.',
    crud: crud('CRUD'),
    crudNote: '삭제 = 폐기(retire, 유효기간 종료 — 물리 삭제 아님).',
    purpose: '회사의 기준 데이터를 관리한다.',
    services: ['erp gateway-service → masterdata-service'],
    sources: [`${API}/erp/masterdata/**/route.ts (GET,POST · retire POST)`],
  },
  {
    href: '/erp/orgview',
    area: 'erp',
    gate: { kind: 'domain', domain: 'erp', roles: ['ERP_OPERATOR'] },
    description: '직원·위임 통합 조회(읽기 모델).',
    crud: R,
    purpose: '여러 마스터를 가로질러 조직을 한 번에 본다.',
    services: ['erp gateway-service → read-model-service'],
    sources: [`${API}/erp/read-model/**/route.ts (GET)`],
  },
  {
    href: '/erp/approval',
    area: 'erp',
    gate: { kind: 'domain', domain: 'erp', roles: ['ERP_OPERATOR'] },
    description: '결재 요청 상신 · 결재함 · 승인/반려 등 상태 전이.',
    crud: crud('CRU'),
    crudNote: '수정 = 결재 상태 전이(transition).',
    purpose: '다단계 결재를 처리한다.',
    services: ['erp gateway-service → approval-service'],
    sources: [
      `${API}/erp/approval/requests/route.ts (GET,POST)`,
      `${API}/erp/approval/requests/[id]/[transition]/route.ts (POST)`,
    ],
  },
  {
    href: '/erp/delegation',
    area: 'erp',
    gate: { kind: 'domain', domain: 'erp', roles: ['ERP_OPERATOR'] },
    description: '결재 위임 목록 · 등록 · 회수.',
    crud: crud('CRD'),
    crudNote: '삭제 = 위임 회수(revoke).',
    purpose: '부재 시 결재 권한을 다른 사람에게 맡긴다.',
    services: ['erp gateway-service → approval-service'],
    sources: [
      `${API}/erp/approval/delegations/route.ts (GET,POST)`,
      `${API}/erp/approval/delegations/[id]/revoke/route.ts (POST)`,
    ],
  },

  // ── E-Commerce ────────────────────────────────────────────────────────
  guideRow('/ecommerce/guide', 'ecommerce', 'E-Commerce'),
  {
    href: '/ecommerce',
    area: 'ecommerce',
    gate: { kind: 'domain', domain: 'ecommerce', roles: ['ECOMMERCE_OPERATOR'] },
    description: '이커머스 도메인 개요(섹션별 요약·상태).',
    crud: R,
    purpose: '스토어 운영 상태를 요약해 본다.',
    services: ['ecommerce gateway-service → 도메인 서비스들'],
    sources: [`${ROLE_DERIVATION}:103`, 'projects/platform-console/apps/console-web/src/features/ecommerce-guide/data.ts:552-556'],
  },
  {
    href: '/ecommerce/products',
    area: 'ecommerce',
    gate: { kind: 'domain', domain: 'ecommerce', roles: ['ECOMMERCE_OPERATOR'] },
    description: '상품 목록 · 등록 · 수정 · 삭제 · 옵션(variant) · 재고 · 이미지.',
    crud: crud('CRUD'),
    purpose: '판매할 상품을 관리한다.',
    services: ['ecommerce gateway-service → product-service'],
    sources: [`${API}/ecommerce/products/route.ts (GET,POST)`, `${API}/ecommerce/products/[id]/route.ts (GET,PATCH,DELETE)`],
  },
  {
    href: '/ecommerce/orders',
    area: 'ecommerce',
    gate: { kind: 'domain', domain: 'ecommerce', roles: ['ECOMMERCE_OPERATOR'] },
    description: '주문 목록 · 상세 · 상태 변경.',
    crud: crud('RU'),
    purpose: '주문을 확인하고 상태를 진행시킨다.',
    services: ['ecommerce gateway-service → order-service'],
    sources: [`${API}/ecommerce/orders/route.ts (GET)`, `${API}/ecommerce/orders/[id]/status/route.ts (POST)`],
  },
  {
    href: '/ecommerce/shippings',
    area: 'ecommerce',
    gate: { kind: 'domain', domain: 'ecommerce', roles: ['ECOMMERCE_OPERATOR'] },
    description: '배송 목록 · 상태 변경 · 추적 새로고침.',
    crud: crud('RU'),
    purpose: '배송을 발송·완료까지 진행시킨다.',
    services: ['ecommerce gateway-service → shipping-service'],
    sources: [`${API}/ecommerce/shippings/[id]/status/route.ts (PUT)`, `${API}/ecommerce/shippings/[id]/refresh-tracking/route.ts (POST)`],
  },
  {
    href: '/ecommerce/promotions',
    area: 'ecommerce',
    gate: { kind: 'domain', domain: 'ecommerce', roles: ['ECOMMERCE_OPERATOR'] },
    description: '프로모션 목록 · 등록 · 수정 · 삭제 · 쿠폰 발급.',
    crud: crud('CRUD'),
    purpose: '할인·쿠폰을 운영한다.',
    services: ['ecommerce gateway-service → promotion-service'],
    sources: [`${API}/ecommerce/promotions/route.ts (GET,POST)`, `${API}/ecommerce/promotions/[id]/route.ts (GET,PUT,DELETE)`],
  },
  {
    href: '/ecommerce/users',
    area: 'ecommerce',
    gate: { kind: 'domain', domain: 'ecommerce', roles: ['ECOMMERCE_OPERATOR'] },
    description: '스토어 사용자 목록 · 상세 조회.',
    crud: R,
    purpose: '구매자 정보를 확인한다.',
    services: ['ecommerce gateway-service → user-service'],
    sources: [`${PAGES}/ecommerce/users/page.tsx:24 (READ-ONLY: no mutation surface)`, `${API}/ecommerce/users/route.ts (GET)`],
  },
  {
    href: '/ecommerce/sellers',
    area: 'ecommerce',
    gate: { kind: 'domain', domain: 'ecommerce', roles: ['ECOMMERCE_OPERATOR'] },
    description: '셀러 목록 · 등록 · 정지 · 폐점 · 프로비저닝.',
    crud: crud('RCU'),
    crudNote: '삭제 없음 — 폐점(close)은 상태 전이.',
    purpose: '입점 셀러의 생명주기를 관리한다.',
    services: ['ecommerce gateway-service → product-service (셀러)'],
    sources: ['projects/platform-console/apps/console-web/src/features/ecommerce-guide/data.ts:56-60 (product-service → 상품 · 셀러)', `${API}/ecommerce/sellers/route.ts (GET,POST)`, `${API}/ecommerce/sellers/[id]/{suspend,close,provision}/route.ts (POST)`],
  },
  {
    href: '/ecommerce/settlements',
    area: 'ecommerce',
    gate: { kind: 'domain', domain: 'ecommerce', roles: ['ECOMMERCE_OPERATOR'] },
    description: '정산 기간 · 적립 · 수수료율 · 지급 실행 · 셀러 잔액.',
    crud: crud('RCU'),
    crudNote: '생성 = 정산 기간 생성 · 수정 = 기간 마감·지급 실행·수수료율 변경.',
    purpose: '셀러 정산을 마감하고 지급한다.',
    services: ['ecommerce gateway-service → settlement-service'],
    sources: [
      `${API}/ecommerce/settlements/periods/route.ts (GET,POST)`,
      `${API}/ecommerce/settlements/periods/[id]/{close,payouts/execute}/route.ts (POST)`,
      `${API}/ecommerce/settlements/commission-rates/[id]/route.ts (GET,PUT)`,
    ],
  },
  {
    href: '/ecommerce/notifications/templates',
    area: 'ecommerce',
    gate: { kind: 'domain', domain: 'ecommerce', roles: ['ECOMMERCE_OPERATOR'] },
    description: '알림 템플릿 목록 · 등록 · 수정.',
    crud: crud('RCU'),
    purpose: '고객에게 가는 알림 문구를 관리한다.',
    services: ['ecommerce gateway-service → notification-service'],
    sources: [
      `${API}/ecommerce/notifications/templates/route.ts (GET,POST)`,
      `${API}/ecommerce/notifications/templates/[id]/route.ts (GET,PUT)`,
    ],
  },
];

/* ─────────────────────────── 파생 ─────────────────────────── */

export type Access = 'yes' | 'partial' | 'no';

/** 데모 테스트 계정이 이 행의 화면을 열 수 있는가 — 원장에서 계산(손으로 적지 않는다). */
export function testAccountAccess(gate: PermissionGate): Access {
  const has = (perm: string) =>
    DEMO_TEST_ACCOUNT.adminRoles.some((role) => RBAC_SEED_MATRIX[perm]?.[role] === true);
  switch (gate.kind) {
    case 'public':
    case 'operator':
      return 'yes';
    case 'admin':
      return has(gate.permission) ? 'yes' : 'no';
    case 'admin-per-card': {
      const n = gate.permissions.filter(has).length;
      return n === gate.permissions.length ? 'yes' : n === 0 ? 'no' : 'partial';
    }
    case 'domain':
      return DEMO_TEST_ACCOUNT.entitledDomains.includes(gate.domain) ? 'yes' : 'no';
  }
}

/** 표시용 권한 코드 문자열. */
export function gateLabel(gate: PermissionGate): string {
  switch (gate.kind) {
    case 'public':
      return '없음 (누구나)';
    case 'operator':
      return '로그인한 운영자';
    case 'admin':
      return gate.permission;
    case 'admin-per-card':
      return `카드별: ${gate.permissions.join(' · ')}`;
    case 'domain':
      return `${gate.domain} 구독 → ${gate.roles[0]}${gate.roles.length > 1 ? ` 외 ${gate.roles.length - 1}` : ''}`;
  }
}

export interface ResolvedRow extends PermissionMapRow {
  label: string;
  depth: 1 | 2;
  /** 사이드바 경로(그룹 › 부모 › 항목). */
  path: string[];
  sample: ScreenStatus;
  access: Access;
}

/** nav 의 모든 leaf 를 순서대로(그룹 · 부모 정보와 함께) 편다. */
export function navLeaves(): { leaf: NavLeaf; depth: 1 | 2; path: string[] }[] {
  const out: { leaf: NavLeaf; depth: 1 | 2; path: string[] }[] = [];
  for (const g of GROUPS) {
    for (const node of g.items) {
      const base = g.label ? [g.label] : [];
      if (isParent(node)) {
        for (const child of node.children) {
          out.push({ leaf: child, depth: 2, path: [...base, node.label, child.label] });
        }
      } else {
        out.push({ leaf: node, depth: 1, path: [...base, node.label] });
      }
    }
  }
  return out;
}

/**
 * nav 순서대로 매핑 행을 조인한다. 🔴 행이 없는 nav href 는 **던진다** — 화면이 조용히
 * 빈 칸을 그리는 대신 빌드/테스트가 빨개지게(드리프트 테스트가 먼저 잡는다).
 */
export function resolvePermissionMap(): ResolvedRow[] {
  const byHref = new Map(PERMISSION_MAP.map((r) => [r.href, r]));
  return navLeaves().map(({ leaf, depth, path }) => {
    const row = byHref.get(leaf.href);
    if (!row) {
      throw new Error(`permission-map: nav href ${leaf.href} has no row (TASK-PC-FE-298 drift)`);
    }
    return {
      ...row,
      label: leaf.label,
      depth,
      path,
      sample: screenStatusFor(leaf.href),
      access: testAccountAccess(row.gate),
    };
  });
}
