/**
 * TASK-PC-FE-244 — console nav-tree data, split out of `ConsoleSidebarNav.tsx`.
 * Pure static data + node types + the `isParent` type guard: no React /
 * framework import. See `ConsoleSidebarNav.tsx` for the rendering component
 * and `console-nav-matching.ts` for the pure route-matching helpers that
 * consume this data.
 */
/**
 * TASK-PC-FE-297 — the sidebar icon for a node. A string key, not a React
 * element, so this file stays framework-free; the glyphs live in
 * `console-nav-icons.tsx`. Required on every node so an item cannot be added
 * without one (the type checker is the guard).
 */
export type NavIconName =
  | 'dashboard'
  | 'catalog'
  | 'guide'
  | 'overview'
  | 'shield'
  | 'user'
  | 'users'
  | 'hierarchy'
  | 'building'
  | 'key'
  | 'lock'
  | 'audit'
  | 'identity'
  | 'subscription'
  | 'partnership'
  | 'warehouse'
  | 'truck'
  | 'wallet'
  | 'ledger'
  | 'cart'
  | 'inbound'
  | 'outbound'
  | 'package'
  | 'database'
  | 'settings'
  | 'clipboard'
  | 'refresh'
  | 'search'
  | 'approval'
  | 'delegation'
  | 'tag'
  | 'receipt'
  | 'percent'
  | 'store'
  | 'settlement'
  | 'bell';

export interface NavLeaf {
  href: string;
  label: string;
  testid: string;
  icon: NavIconName;
}
export interface NavParent {
  key: string;
  label: string;
  testid: string;
  icon: NavIconName;
  children: NavLeaf[];
}
export type NavNode = NavLeaf | NavParent;
export interface NavGroup {
  label?: string;
  testid?: string;
  items: NavNode[];
}

export function isParent(node: NavNode): node is NavParent {
  return (node as NavParent).children !== undefined;
}

export const GROUPS: NavGroup[] = [
  {
    items: [
      { href: '/dashboards/overview', label: '개요', testid: 'nav-dashboards', icon: 'dashboard' },
      // 도메인 상태(/dashboards/health) is NOT a top-level entry (TASK-PC-FE-068)
      // — it is reached only from the 개요 page's "도메인 상태 요약" card
      // "전체 보기 →" link (PC-FE-061), and that page carries a back link to the
      // overview. Keeps the top group to the 1-click home + catalog.
      { href: '/console', label: '카탈로그', testid: 'nav-catalog', icon: 'catalog' },
    ],
  },
  {
    label: '관리',
    items: [
      {
        // TASK-PC-FE-225 — orthodox IAM taxonomy: this drill parent is now the
        // **workforce plane** only (AWS IAM / GCP Cloud IAM equivalent) —
        // 테넌트 (isolation boundary, AWS account / GCP project) · 운영자
        // (workforce identity, IAM User) · 운영자 그룹 (IAM User Group /
        // Google Group, ADR-MONO-046) · 권한 (Action/Permission) · 권한 세트
        // (IAM Policy/Role). The consumer-facing 계정(/accounts) surface moved
        // OUT to its own 「고객 신원」 group below (Cognito / Identity Platform
        // equivalent) — nav placement only, route/features unchanged
        // (Catalog iam.baseRoute stays /accounts, FE-002).
        key: 'iam',
        label: 'IAM',
        testid: 'nav-iam', icon: 'shield',
        children: [
          // TASK-PC-FE-297 (owner decision 2026-09-24 UTC) — 가이드 FIRST, then
          // 개요, in EVERY domain. This deliberately reverses the earlier
          // 개요→가이드 order (TASK-PC-FE-180 put the LIVE 개요 first as the
          // operator landing): the console is now read first by portfolio
          // visitors (ADR-MONO-074 sample mode), and the guide — static, no
          // backend call — is the orientation they need before a live
          // snapshot means anything. Route landing is unchanged (`/iam` still
          // renders 개요; deep links still activate the matching child); only
          // the DOM order of the two siblings flipped (testids
          // `nav-iam-guide` / `nav-iam-overview` unchanged).
          // 가이드(/iam/guide) = the static RBAC reference; 개요(/iam —
          // TASK-PC-FE-180) = the LIVE operator snapshot (운영자·계정·감사 현황).
          // Then the workforce-plane management (write) surfaces in
          // **setup-first** order — 운영자 관리 (provision the operators)
          // immediately followed by 운영자 그룹 (bulk-grant roles to a group
          // of operators, ADR-MONO-046) — then the isolation/permission
          // surfaces 테넌트 · 권한 · 권한 세트 — then 감사·보안 (read-only
          // oversight) last: learn → orient → configure → operate → review.
          { href: '/iam/guide', label: '가이드', testid: 'nav-iam-guide', icon: 'guide' },
          { href: '/iam', label: '개요', testid: 'nav-iam-overview', icon: 'overview' },
          { href: '/operators', label: '운영자 관리', testid: 'nav-operators', icon: 'user' },
          // 운영자 그룹 (TASK-PC-FE-250 / ADR-MONO-046) — IAM User Group /
          // Google Group equivalent: bundle operators + bulk-grant roles /
          // tenant-assignments (fan-out).
          {
            href: '/operator-groups',
            label: '운영자 그룹',
            testid: 'nav-iam-operator-groups', icon: 'users',
          },
          // 조직 계층 (TASK-PC-FE-237 / ADR-047) — company → service → domain
          // 3-axis hierarchy (org-node tree + entitlement ceiling + ORG_ADMIN).
          // Placed BEFORE 테넌트: a company (org node) sits above its
          // service-tenants (AWS Organizations account-group above accounts).
          {
            href: '/org-hierarchy',
            label: '조직 계층',
            testid: 'nav-iam-org-hierarchy', icon: 'hierarchy',
          },
          // 테넌트 (real feature = TASK-PC-FE-226) — isolation boundary,
          // AWS account / GCP project equivalent.
          { href: '/tenants', label: '테넌트', testid: 'nav-iam-tenants', icon: 'building' },
          // 권한 (real feature = TASK-PC-FE-227) — Action/Permission
          // equivalent.
          {
            href: '/permissions',
            label: '권한',
            testid: 'nav-iam-permissions', icon: 'key',
          },
          // 권한 세트 (real feature = TASK-PC-FE-228) — IAM Policy/Role
          // equivalent.
          {
            href: '/permission-sets',
            label: '권한 세트',
            testid: 'nav-iam-permission-sets', icon: 'lock',
          },
          { href: '/audit', label: '감사 · 보안', testid: 'nav-audit', icon: 'audit' },
        ],
      },
    ],
  },
  {
    // TASK-PC-FE-225 — the consumer-facing / B2C identity plane (AWS Cognito
    // / GCP Identity Platform equivalent), split OUT of the workforce IAM
    // group above (orthodox IAM taxonomy: workforce plane vs. customer
    // identity plane are distinct surfaces even though both are "identity").
    // A single flat leaf today (계정 운영, unchanged route/features/gating —
    // nav placement only); not a drill parent since it has one destination.
    label: '고객 신원',
    testid: 'nav-group-customer-identity',
    items: [
      { href: '/accounts', label: '계정 운영', testid: 'nav-accounts', icon: 'identity' },
    ],
  },
  {
    // Entitlement plane (ADR-MONO-023) — kept as its own group, distinct from
    // the IAM identity plane above: a tenant owner (TENANT_BILLING_ADMIN)
    // self-enables domains for their tenant (TASK-PC-FE-183, the piece that
    // makes self-service onboarding PC-FE-182 usable).
    label: '조직 설정',
    items: [
      { href: '/subscriptions', label: '도메인 구독', testid: 'nav-subscriptions', icon: 'subscription' },
      // Cross-org partner delegation (ADR-MONO-045 §3.4 / TASK-PC-FE-187) — a
      // tenant owner (TENANT_ADMIN, partnership.manage) manages cross-org
      // partnerships for their tenant, alongside the domain subscriptions.
      { href: '/partnerships', label: '파트너십', testid: 'nav-partnerships', icon: 'partnership' },
    ],
  },
  {
    label: '도메인 운영',
    items: [
      {
        key: 'wms',
        label: 'WMS',
        testid: 'nav-wms', icon: 'warehouse',
        children: [
          // 가이드(/wms/guide — TASK-PC-FE-183): 재고·출고 개념 정적 참조.
          // TASK-PC-FE-297(2026-09-24 UTC 소유자 결정): 모든 도메인 공통
          // 「가이드 → 개요 → 기능」 순서 — IAM 주석 참조. 가이드가 맨 앞.
          { href: '/wms/guide', label: '가이드', testid: 'nav-wms-guide', icon: 'guide' },
          { href: '/wms', label: '개요', testid: 'nav-wms-ops', icon: 'overview' },
          // 입고(/wms/inbound — TASK-PC-FE-222): ASN(입고예정) + 검수 조회
          // 전용 화면. 물류 흐름(입고→재고→출고)을 그대로 반영해 개요와
          // 재고 사이에 둔다 — 출고(/wms/outbound)와 대칭인 입고 진입점.
          { href: '/wms/inbound', label: '입고', testid: 'nav-wms-inbound', icon: 'inbound' },
          {
            href: '/wms/inventory',
            label: '재고',
            testid: 'nav-wms-inventory', icon: 'package',
          },
          { href: '/wms/outbound', label: '출고', testid: 'nav-wms-outbound', icon: 'outbound' },
          // 마스터(/wms/master — TASK-PC-FE-223): 창고/구역/로케이션/SKU/Lot/
          // 거래처 참조 데이터 read-only 조회. 참조/설정 성격이라 물류 흐름
          // (입고→재고→출고) 뒤에 둔다.
          { href: '/wms/master', label: '마스터', testid: 'nav-wms-master', icon: 'database' },
          // 운영설정(/wms/operations — TASK-PC-FE-224): 예약 TTL·저재고
          // 기본 임계치 등 운영 설정 + read-model 프로젝션 상태 read-only
          // 조회. 참조/설정 성격의 마스터보다도 더 후순위(운영 파라미터)라
          // 맨 끝에 둔다.
          {
            href: '/wms/operations',
            label: '운영설정',
            testid: 'nav-wms-operations', icon: 'settings',
          },
        ],
      },
      {
        // SCM is a drill-in parent (same model as WMS): 가이드(/scm/guide —
        // TASK-PC-FE-188 static reference, FIRST per the all-domain
        // 가이드 → 개요 order, TASK-PC-FE-297) + 개요(/scm — the overview
        // snapshot band only, PC-FE-167/220) + 조달(/scm/procurement —
        // the read-only PO list split out of 개요, PC-FE-220) + 재고
        // (/scm/inventory — the read-only inventory-visibility snapshot/SKU/
        // staleness split out of 개요, PC-FE-220) + 보충 계획(/scm/replenishment
        // — the FE-077 replenishment operator gate) + 보충 계획 설정(/scm/config
        // — the FE-080 seed/config operator surface: per-SKU reorder-policy +
        // sku-supplier-map upsert, the operational fix-path for the 보충
        // SKU_SUPPLIER_UNMAPPED gap). TASK-PC-FE-220 split 개요's combined
        // procurement + inventory tables into their own 조달/재고 routes and
        // renamed 보충/설정 → 보충 계획/보충 계획 설정 (href + testid unchanged).
        // The /scm destination lives on the 개요 child (nav-scm-ops); nav-scm
        // is the pinned parent back-toggle.
        key: 'scm',
        label: 'SCM',
        testid: 'nav-scm', icon: 'truck',
        children: [
          { href: '/scm/guide', label: '가이드', testid: 'nav-scm-guide', icon: 'guide' },
          { href: '/scm', label: '개요', testid: 'nav-scm-ops', icon: 'overview' },
          {
            href: '/scm/procurement',
            label: '조달',
            testid: 'nav-scm-procurement', icon: 'clipboard',
          },
          {
            href: '/scm/inventory',
            label: '재고',
            testid: 'nav-scm-inventory', icon: 'package',
          },
          {
            href: '/scm/replenishment',
            label: '보충 계획',
            testid: 'nav-scm-replenishment', icon: 'refresh',
          },
          {
            href: '/scm/config',
            label: '보충 계획 설정',
            testid: 'nav-scm-config', icon: 'settings',
          },
        ],
      },
      {
        // Finance is ONE domain (finance-platform) with TWO bound console
        // surfaces — account-service (계좌: 계좌·잔액·거래) + ledger-service
        // (원장: 시산표·기간·대조, TASK-PC-FE-072). They share the finance
        // tenant gate + a single entitlement (entitled_domains ∋ finance gates
        // BOTH), so they nest under one Finance drill parent — the SAME model
        // as WMS (개요 + 재고 + 출고), IAM, SCM, and ERP. TASK-PC-FE-078
        // (was two flat sibling leaves nav-finance + nav-ledger).
        // TASK-PC-FE-229 — 정석(orthodox) 파리티 정렬: `/finance`(구 계좌/운영
        // 표면)를 `/finance/accounts`로 이동(라벨 `운영`→`계좌`, testid
        // `nav-finance-ops`→`nav-finance-accounts`)하고, 도메인 루트
        // `/finance`를 IAM/WMS/SCM/E-Commerce 와 동일하게 **개요** 랜딩으로
        // 교체 + **가이드**(`/finance/guide`) 신설. 순서 = 가이드 → 개요 →
        // 계좌 → 원장(TASK-PC-FE-297 — 모든 도메인의 가이드→개요→기능 순서와
        // 일치, 2026-09-24 UTC 소유자 결정으로 옛 개요→가이드를 뒤집음). 부모
        // testid `nav-finance` 는 유지.
        key: 'finance',
        label: 'Finance',
        testid: 'nav-finance', icon: 'wallet',
        children: [
          { href: '/finance/guide', label: '가이드', testid: 'nav-finance-guide', icon: 'guide' },
          { href: '/finance', label: '개요', testid: 'nav-finance-overview', icon: 'overview' },
          { href: '/finance/accounts', label: '계좌', testid: 'nav-finance-accounts', icon: 'wallet' },
          { href: '/ledger', label: '원장', testid: 'nav-ledger', icon: 'ledger' },
        ],
      },
      {
        // TASK-PC-FE-076 — ERP becomes a drill parent (same model as WMS):
        // the single dense `/erp` page split into section routes.
        // TASK-PC-FE-232 — 정석(orthodox) 파리티 정렬: 구 마스터 표면을
        // `/erp/masters`로 이동(testid `nav-erp-masters` 유지)하고, 도메인
        // 루트 `/erp`를 IAM/WMS/SCM/E-Commerce/Finance 와 동일하게 **개요**
        // 랜딩으로 교체 + **가이드**(`/erp/guide`) 신설. 순서 = 가이드 → 개요
        // → 마스터 → 통합 조회 → 결재함 → 위임(TASK-PC-FE-297 — 모든 도메인의
        // 가이드→개요→기능 순서와 일치). 부모 testid `nav-erp` 는 유지.
        key: 'erp',
        label: 'ERP',
        testid: 'nav-erp', icon: 'building',
        children: [
          { href: '/erp/guide', label: '가이드', testid: 'nav-erp-guide', icon: 'guide' },
          { href: '/erp', label: '개요', testid: 'nav-erp-overview', icon: 'overview' },
          { href: '/erp/masters', label: '마스터', testid: 'nav-erp-masters', icon: 'database' },
          { href: '/erp/orgview', label: '통합 조회', testid: 'nav-erp-orgview', icon: 'search' },
          { href: '/erp/approval', label: '결재함', testid: 'nav-erp-approval', icon: 'approval' },
          {
            href: '/erp/delegation',
            label: '위임',
            testid: 'nav-erp-delegation', icon: 'delegation',
          },
        ],
      },
      {
        // ecommerce is a drill-in parent (same model as WMS): 가이드 first
        // (TASK-PC-FE-297), then 개요(/ecommerce —
        // the MONO-241 health/section page) + 상품(/ecommerce/products — the
        // PC-FE-081 product operator CRUD surface, § 2.4.10) + 주문
        // (/ecommerce/orders — the PC-FE-083 order operator surface, § 2.4.10).
        // The /ecommerce destination lives on the 운영 child (nav-ecommerce-ops);
        // nav-ecommerce is the pinned parent back-toggle. Image (presigned) is a
        // later facet (PC-FE-082).
        key: 'ecommerce',
        label: 'E-Commerce',
        testid: 'nav-ecommerce', icon: 'cart',
        children: [
          // 가이드(/ecommerce/guide — TASK-PC-FE-184): 도메인 서비스·주문·배송·
          // 상품·프로모션·셀러·사용자·알림 정적 참조. TASK-PC-FE-297 — 모든
          // 도메인 공통 가이드→개요 순서로 맨 앞(개요 앞)에 둔다.
          {
            href: '/ecommerce/guide',
            label: '가이드',
            testid: 'nav-ecommerce-guide', icon: 'guide',
          },
          { href: '/ecommerce', label: '개요', testid: 'nav-ecommerce-ops', icon: 'overview' },
          {
            href: '/ecommerce/products',
            label: '상품',
            testid: 'nav-ecommerce-products', icon: 'tag',
          },
          {
            href: '/ecommerce/orders',
            label: '주문',
            testid: 'nav-ecommerce-orders', icon: 'receipt',
          },
          {
            href: '/ecommerce/shippings',
            label: '배송',
            testid: 'nav-ecommerce-shippings', icon: 'truck',
          },
          {
            href: '/ecommerce/promotions',
            label: '프로모션',
            testid: 'nav-ecommerce-promotions', icon: 'percent',
          },
          {
            href: '/ecommerce/users',
            label: '사용자',
            testid: 'nav-ecommerce-users', icon: 'users',
          },
          {
            href: '/ecommerce/sellers',
            label: '셀러',
            testid: 'nav-ecommerce-sellers', icon: 'store',
          },
          {
            href: '/ecommerce/settlements',
            label: '정산',
            testid: 'nav-ecommerce-settlements', icon: 'settlement',
          },
          {
            href: '/ecommerce/notifications/templates',
            label: '알림',
            testid: 'nav-ecommerce-notifications', icon: 'bell',
          },
        ],
      },
    ],
  },
];
