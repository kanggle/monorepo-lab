/**
 * TASK-PC-FE-244 — console nav-tree data, split out of `ConsoleSidebarNav.tsx`.
 * Pure static data + node types + the `isParent` type guard: no React /
 * framework import. See `ConsoleSidebarNav.tsx` for the rendering component
 * and `console-nav-matching.ts` for the pure route-matching helpers that
 * consume this data.
 */
export interface NavLeaf {
  href: string;
  label: string;
  testid: string;
}
export interface NavParent {
  key: string;
  label: string;
  testid: string;
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
      { href: '/dashboards/overview', label: '개요', testid: 'nav-dashboards' },
      // 도메인 상태(/dashboards/health) is NOT a top-level entry (TASK-PC-FE-068)
      // — it is reached only from the 개요 page's "도메인 상태 요약" card
      // "전체 보기 →" link (PC-FE-061), and that page carries a back link to the
      // overview. Keeps the top group to the 1-click home + catalog.
      { href: '/console', label: '카탈로그', testid: 'nav-catalog' },
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
        testid: 'nav-iam',
        children: [
          // 개요(/iam — TASK-PC-FE-180) is the first child: a LIVE operator
          // overview snapshot (운영자·계정·감사 현황, direct fan-out over the IAM
          // list endpoints) so an operator sees the domain state at a glance —
          // consistent with every other domain's 개요. The former static RBAC
          // guide is relocated to 가이드(/iam/guide, testid `nav-iam-guide`
          // preserved) as the second child. Then the workforce-plane
          // management (write) surfaces in **setup-first** order — 운영자
          // 관리 (provision the operators) immediately followed by 운영자
          // 그룹 (bulk-grant roles to a group of operators, ADR-MONO-046) —
          // then the isolation/permission surfaces 테넌트 · 권한 · 권한 세트 —
          // then 감사·보안 (read-only oversight) last: orient → learn →
          // configure → operate → review.
          { href: '/iam', label: '개요', testid: 'nav-iam-overview' },
          { href: '/iam/guide', label: '가이드', testid: 'nav-iam-guide' },
          { href: '/operators', label: '운영자 관리', testid: 'nav-operators' },
          // 운영자 그룹 (TASK-PC-FE-250 / ADR-MONO-046) — IAM User Group /
          // Google Group equivalent: bundle operators + bulk-grant roles /
          // tenant-assignments (fan-out).
          {
            href: '/operator-groups',
            label: '운영자 그룹',
            testid: 'nav-iam-operator-groups',
          },
          // 조직 계층 (TASK-PC-FE-237 / ADR-047) — company → service → domain
          // 3-axis hierarchy (org-node tree + entitlement ceiling + ORG_ADMIN).
          // Placed BEFORE 테넌트: a company (org node) sits above its
          // service-tenants (AWS Organizations account-group above accounts).
          {
            href: '/org-hierarchy',
            label: '조직 계층',
            testid: 'nav-iam-org-hierarchy',
          },
          // 테넌트 (TASK-PC-FE-225 stub; real feature = TASK-PC-FE-226) —
          // isolation boundary, AWS account / GCP project equivalent.
          { href: '/tenants', label: '테넌트', testid: 'nav-iam-tenants' },
          // 권한 (TASK-PC-FE-225 stub; real feature = TASK-PC-FE-227) —
          // Action/Permission equivalent.
          {
            href: '/permissions',
            label: '권한',
            testid: 'nav-iam-permissions',
          },
          // 권한 세트 (TASK-PC-FE-225 stub; real feature = TASK-PC-FE-228) —
          // IAM Policy/Role equivalent.
          {
            href: '/permission-sets',
            label: '권한 세트',
            testid: 'nav-iam-permission-sets',
          },
          { href: '/audit', label: '감사 · 보안', testid: 'nav-audit' },
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
      { href: '/accounts', label: '계정 운영', testid: 'nav-accounts' },
    ],
  },
  {
    // Entitlement plane (ADR-MONO-023) — kept as its own group, distinct from
    // the IAM identity plane above: a tenant owner (TENANT_BILLING_ADMIN)
    // self-enables domains for their tenant (TASK-PC-FE-183, the piece that
    // makes self-service onboarding PC-FE-182 usable).
    label: '조직 설정',
    items: [
      { href: '/subscriptions', label: '도메인 구독', testid: 'nav-subscriptions' },
      // Cross-org partner delegation (ADR-MONO-045 §3.4 / TASK-PC-FE-187) — a
      // tenant owner (TENANT_ADMIN, partnership.manage) manages cross-org
      // partnerships for their tenant, alongside the domain subscriptions.
      { href: '/partnerships', label: '파트너십', testid: 'nav-partnerships' },
    ],
  },
  {
    label: '도메인 운영',
    items: [
      {
        key: 'wms',
        label: 'WMS',
        testid: 'nav-wms',
        children: [
          { href: '/wms', label: '개요', testid: 'nav-wms-ops' },
          // 가이드(/wms/guide — TASK-PC-FE-183): 재고·출고 개념 정적 참조.
          // IAM 의 개요→가이드 순서와 동일하게 개요 다음(재고 앞)에 둔다.
          { href: '/wms/guide', label: '가이드', testid: 'nav-wms-guide' },
          // 입고(/wms/inbound — TASK-PC-FE-222): ASN(입고예정) + 검수 조회
          // 전용 화면. 물류 흐름(입고→재고→출고)을 그대로 반영해 가이드와
          // 재고 사이에 둔다 — 출고(/wms/outbound)와 대칭인 입고 진입점.
          { href: '/wms/inbound', label: '입고', testid: 'nav-wms-inbound' },
          {
            href: '/wms/inventory',
            label: '재고',
            testid: 'nav-wms-inventory',
          },
          { href: '/wms/outbound', label: '출고', testid: 'nav-wms-outbound' },
          // 마스터(/wms/master — TASK-PC-FE-223): 창고/구역/로케이션/SKU/Lot/
          // 거래처 참조 데이터 read-only 조회. 참조/설정 성격이라 물류 흐름
          // (입고→재고→출고) 뒤에 둔다.
          { href: '/wms/master', label: '마스터', testid: 'nav-wms-master' },
          // 운영설정(/wms/operations — TASK-PC-FE-224): 예약 TTL·저재고
          // 기본 임계치 등 운영 설정 + read-model 프로젝션 상태 read-only
          // 조회. 참조/설정 성격의 마스터보다도 더 후순위(운영 파라미터)라
          // 맨 끝에 둔다.
          {
            href: '/wms/operations',
            label: '운영설정',
            testid: 'nav-wms-operations',
          },
        ],
      },
      {
        // SCM is a drill-in parent (same model as WMS): 개요(/scm — the
        // overview snapshot band only, PC-FE-167/220) + 가이드(/scm/guide —
        // TASK-PC-FE-188 static reference, placed 개요 다음 per
        // IAM/WMS/E-Commerce's 개요 → 가이드 order) + 조달(/scm/procurement —
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
        testid: 'nav-scm',
        children: [
          { href: '/scm', label: '개요', testid: 'nav-scm-ops' },
          { href: '/scm/guide', label: '가이드', testid: 'nav-scm-guide' },
          {
            href: '/scm/procurement',
            label: '조달',
            testid: 'nav-scm-procurement',
          },
          {
            href: '/scm/inventory',
            label: '재고',
            testid: 'nav-scm-inventory',
          },
          {
            href: '/scm/replenishment',
            label: '보충 계획',
            testid: 'nav-scm-replenishment',
          },
          {
            href: '/scm/config',
            label: '보충 계획 설정',
            testid: 'nav-scm-config',
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
        // 교체 + **가이드**(`/finance/guide`) 신설. 순서 = 개요 → 가이드 →
        // 계좌 → 원장(모든 도메인의 개요→가이드→기능 순서와 일치). 부모
        // testid `nav-finance` 는 유지.
        key: 'finance',
        label: 'Finance',
        testid: 'nav-finance',
        children: [
          { href: '/finance', label: '개요', testid: 'nav-finance-overview' },
          { href: '/finance/guide', label: '가이드', testid: 'nav-finance-guide' },
          { href: '/finance/accounts', label: '계좌', testid: 'nav-finance-accounts' },
          { href: '/ledger', label: '원장', testid: 'nav-ledger' },
        ],
      },
      {
        // TASK-PC-FE-076 — ERP becomes a drill parent (same model as WMS):
        // the single dense `/erp` page split into section routes.
        // TASK-PC-FE-232 — 정석(orthodox) 파리티 정렬: 구 마스터 표면을
        // `/erp/masters`로 이동(testid `nav-erp-masters` 유지)하고, 도메인
        // 루트 `/erp`를 IAM/WMS/SCM/E-Commerce/Finance 와 동일하게 **개요**
        // 랜딩으로 교체 + **가이드**(`/erp/guide`) 신설. 순서 = 개요 → 가이드
        // → 마스터 → 통합 조회 → 결재함 → 위임(모든 도메인의 개요→가이드→
        // 기능 순서와 일치). 부모 testid `nav-erp` 는 유지.
        key: 'erp',
        label: 'ERP',
        testid: 'nav-erp',
        children: [
          { href: '/erp', label: '개요', testid: 'nav-erp-overview' },
          { href: '/erp/guide', label: '가이드', testid: 'nav-erp-guide' },
          { href: '/erp/masters', label: '마스터', testid: 'nav-erp-masters' },
          { href: '/erp/orgview', label: '통합 조회', testid: 'nav-erp-orgview' },
          { href: '/erp/approval', label: '결재함', testid: 'nav-erp-approval' },
          {
            href: '/erp/delegation',
            label: '위임',
            testid: 'nav-erp-delegation',
          },
        ],
      },
      {
        // ecommerce is a drill-in parent (same model as WMS): 운영(/ecommerce —
        // the MONO-241 health/section page) + 상품(/ecommerce/products — the
        // PC-FE-081 product operator CRUD surface, § 2.4.10) + 주문
        // (/ecommerce/orders — the PC-FE-083 order operator surface, § 2.4.10).
        // The /ecommerce destination lives on the 운영 child (nav-ecommerce-ops);
        // nav-ecommerce is the pinned parent back-toggle. Image (presigned) is a
        // later facet (PC-FE-082).
        key: 'ecommerce',
        label: 'E-Commerce',
        testid: 'nav-ecommerce',
        children: [
          { href: '/ecommerce', label: '개요', testid: 'nav-ecommerce-ops' },
          // 가이드(/ecommerce/guide — TASK-PC-FE-184): 도메인 서비스·주문·배송·
          // 상품·프로모션·셀러·사용자·알림 정적 참조. IAM·WMS 의 개요→가이드
          // 순서와 동일하게 개요 다음(상품 앞)에 둔다.
          {
            href: '/ecommerce/guide',
            label: '가이드',
            testid: 'nav-ecommerce-guide',
          },
          {
            href: '/ecommerce/products',
            label: '상품',
            testid: 'nav-ecommerce-products',
          },
          {
            href: '/ecommerce/orders',
            label: '주문',
            testid: 'nav-ecommerce-orders',
          },
          {
            href: '/ecommerce/shippings',
            label: '배송',
            testid: 'nav-ecommerce-shippings',
          },
          {
            href: '/ecommerce/promotions',
            label: '프로모션',
            testid: 'nav-ecommerce-promotions',
          },
          {
            href: '/ecommerce/users',
            label: '사용자',
            testid: 'nav-ecommerce-users',
          },
          {
            href: '/ecommerce/sellers',
            label: '셀러',
            testid: 'nav-ecommerce-sellers',
          },
          {
            href: '/ecommerce/settlements',
            label: '정산',
            testid: 'nav-ecommerce-settlements',
          },
          {
            href: '/ecommerce/notifications/templates',
            label: '알림',
            testid: 'nav-ecommerce-notifications',
          },
        ],
      },
    ],
  },
];
