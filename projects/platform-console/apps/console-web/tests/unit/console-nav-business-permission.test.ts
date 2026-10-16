/**
 * TASK-PC-FE-338 c-2 (AC-1 · AC-2) — the per-menu «업무 권한 없음» signal and the write-button gate.
 * Contract § 2.4.3.4 «Domain nav — two signals» (per menu, owner decision 2026-10-15) and
 * «Read-only sets hide write buttons».
 *
 * 🔴 Fixtures follow the server measurement recorded in the ticket's c-2 AC-0 table, not intuition:
 *   - `/wms/outbound` → outbound-service `GET /api/**` needs OUTBOUND_READ ∨ WRITE ∨ ADMIN
 *     (live, 27th window: inventory set → 403);
 *   - `/wms`, `/wms/inventory`, `/wms/inbound`, `/wms/master` → wms admin-service dashboards open on
 *     subscription alone (`WMS_VIEWER` synthesised from `entitled_domains`) → 200 for any set;
 *   - E-commerce (TASK-PC-FE-345, server = TASK-MONO-795, live 28th window): each area's service
 *     re-checks GET = <AREA>_READ ∨ _WRITE (members: MEMBER_READ only; sellers = product area);
 *     `ECOMMERCE_OPERATOR` is an auth-derived entry role, deleted from every set by V0059, so row 12
 *     NEVER carries it — c-2's `serverRequires = ECOMMERCE_OPERATOR` badged every E-commerce menu
 *     for every operator (live regression from the 795 bake, 2026-10-16);
 *   - ERP (TASK-PC-FE-343, server = TASK-MONO-793, live 28th window): no subscription-synthesised
 *     read any more; masterdata reads + read-model `employees*` = MD_READ ∨ MD_WRITE, approval reads
 *     = AP_READ ∨ AP_WRITE; `ERP_OPERATOR` is an auth-derived entry role, absent from row 12;
 *   - Finance reads → open on subscription alone (until TASK-PC-FE-344);
 *   - SCM (TASK-PC-FE-342, server = TASK-MONO-792, live 28th window): each SCM service's GET =
 *     its READ ∨ WRITE role, mutations = WRITE; `SCM_OPERATOR` is an auth-derived entry role
 *     that opens no business API and never appears in row 12; WMS 출고 «발송 재시도» goes to
 *     logistics-service = SCM_LOGISTICS_WRITE (Q3 strict — live: outbound set only → 403).
 * A badge on a leaf the server serves (or no badge on a leaf it refuses) is the failure this
 * pins.
 */
import { describe, it, expect } from 'vitest';
import {
  businessPermissionBadgeKeys,
  businessRolesWithin,
  canWriteOn,
  subscriptionBadgeKeys,
  type DomainKey,
} from '@/shared/ui/console-nav-exposure';
import { GROUPS } from '@/shared/ui/console-nav-config';
import { PERMISSION_MAP } from '@/shared/guide/permission-map';

const subs = (...d: DomainKey[]) => new Set<DomainKey>(d);
const roles = (...r: string[]) => new Set<string>(r);

const WMS_SUBSCRIPTION_ONLY = ['/wms', '/wms/inventory', '/wms/inbound', '/wms/master'];
const ECOMMERCE_LEAVES = PERMISSION_MAP.filter(
  (r) => r.gate.kind === 'domain' && r.gate.domain === 'ecommerce',
).map((r) => r.href);

/**
 * The admin-service catalog (admin-api.md § 카탈로그 · `DomainRoleCatalog.java`). The SCM part is
 * the TASK-MONO-792 catalog (8 business roles; `SCM_OPERATOR` removed — auth derives it); the
 * E-commerce part is the TASK-MONO-795 catalog (13 business roles; `ECOMMERCE_OPERATOR` removed);
 * the ERP part is the TASK-MONO-793 catalog (4 business roles; `ERP_OPERATOR` removed). The
 * Finance entry is still the pre-P7 one; TASK-PC-FE-344 replaces it.
 */
const ERP_CATALOG = [
  'ERP_MASTERDATA_READ', 'ERP_MASTERDATA_WRITE', 'ERP_APPROVAL_READ', 'ERP_APPROVAL_WRITE',
];
const SCM_CATALOG = [
  'SCM_PROCUREMENT_READ', 'SCM_PROCUREMENT_WRITE', 'SCM_DEMAND_PLANNING_READ',
  'SCM_DEMAND_PLANNING_WRITE', 'SCM_LOGISTICS_READ', 'SCM_LOGISTICS_WRITE',
  'SCM_INVENTORY_VISIBILITY_READ', 'SCM_INVENTORY_VISIBILITY_WRITE',
];
const ECOMMERCE_CATALOG = [
  'ECOMMERCE_ORDER_READ', 'ECOMMERCE_ORDER_WRITE', 'ECOMMERCE_PRODUCT_READ',
  'ECOMMERCE_PRODUCT_WRITE', 'ECOMMERCE_MEMBER_READ', 'ECOMMERCE_SETTLEMENT_READ',
  'ECOMMERCE_SETTLEMENT_WRITE', 'ECOMMERCE_PROMOTION_READ', 'ECOMMERCE_PROMOTION_WRITE',
  'ECOMMERCE_SHIPPING_READ', 'ECOMMERCE_SHIPPING_WRITE', 'ECOMMERCE_NOTIFICATION_READ',
  'ECOMMERCE_NOTIFICATION_WRITE',
];
const CATALOG_ROLES = new Set([
  'INVENTORY_READ', 'INVENTORY_WRITE', 'INBOUND_READ', 'INBOUND_WRITE', 'OUTBOUND_READ',
  'OUTBOUND_WRITE', 'MASTER_READ', 'WMS_OPERATOR', ...SCM_CATALOG, ...ERP_CATALOG,
  'FINANCE_OPERATOR', ...ECOMMERCE_CATALOG, 'FAN_OPERATOR', 'MES_OPERATOR',
]);

/**
 * Entry roles auth DERIVES (TASK-MONO-792..795) — never in the catalog, never in row 12. A
 * declaration naming one badges / hides buttons for every operator of that domain.
 */
const ENTRY_ROLES = ['SCM_OPERATOR', 'ERP_OPERATOR', 'FINANCE_OPERATOR', 'ECOMMERCE_OPERATOR'];

/** Row 12 of a legacy-compat / `ecommerce-full` operator after V0059: the 13 roles, no entry role. */
const ECOMMERCE_FULL_ROW12 = ECOMMERCE_CATALOG;
/** System preset `ecommerce-read` (V0059). */
const ECOMMERCE_READ_SET = ECOMMERCE_CATALOG.filter((r) => r.endsWith('_READ'));
/** System preset `ecommerce-order-operate` (V0059). */
const ECOMMERCE_ORDER_SET = ['ECOMMERCE_ORDER_READ', 'ECOMMERCE_ORDER_WRITE'];

const SCM_LEAVES = ['/scm', '/scm/procurement', '/scm/inventory', '/scm/replenishment', '/scm/config'];
/** System preset `scm-read` (TASK-MONO-792) — every SCM READ role. */
const SCM_READ_SET = [
  'SCM_PROCUREMENT_READ', 'SCM_DEMAND_PLANNING_READ', 'SCM_LOGISTICS_READ',
  'SCM_INVENTORY_VISIBILITY_READ',
];
/** System preset `scm-demand-planning-operate`. */
const SCM_DEMAND_PLANNING_SET = ['SCM_DEMAND_PLANNING_READ', 'SCM_DEMAND_PLANNING_WRITE'];
/** System preset `wms-outbound-operate` (no SCM role). */
const WMS_OUTBOUND_SET = ['OUTBOUND_READ', 'OUTBOUND_WRITE'];

describe('AC-1 — inventory-set operator on a WMS-only tenant', () => {
  const badges = businessPermissionBadgeKeys(
    GROUPS,
    subs('wms'),
    roles('INVENTORY_READ', 'INVENTORY_WRITE'),
  );

  it('출고 gets «업무 권한 없음» (server: outbound-service 403)', () => {
    expect(badges.has('/wms/outbound')).toBe(true);
  });

  it('재고 · 입고 · 마스터 · 개요 do not (server: admin-service dashboards 200)', () => {
    for (const href of WMS_SUBSCRIPTION_ONLY) expect(badges.has(href)).toBe(false);
  });

  it('the WMS parent itself is not badged — not every child is refused', () => {
    expect(badges.has('wms')).toBe(false);
  });

  it('SCM (not subscribed) gets only «구독 필요», never «업무 권한 없음» — one cause per item', () => {
    expect(subscriptionBadgeKeys(GROUPS, subs('wms')).has('scm')).toBe(true);
    for (const href of [...badges]) expect(href.startsWith('/scm')).toBe(false);
    expect(badges.has('scm')).toBe(false);
  });
});

describe('AC-1 — control: the outbound set', () => {
  it('OUTBOUND_READ alone opens 출고 (no badge)', () => {
    const badges = businessPermissionBadgeKeys(GROUPS, subs('wms'), roles('OUTBOUND_READ'));
    expect(badges.has('/wms/outbound')).toBe(false);
  });
});

/**
 * TASK-PC-FE-345 — E-commerce per area (server: TASK-MONO-795, each service re-checks; live 28th
 * window: `X-User-Role: ECOMMERCE_OPERATOR` alone → order/product 403, with ORDER_READ /
 * PRODUCT_READ → 200; V0059 left 0 sets holding ECOMMERCE_OPERATOR).
 */
describe('TASK-PC-FE-345 — E-commerce menus follow the area roles', () => {
  it('🔴 live regression pin: a legacy-compat operator\'s row 12 (13 roles, no entry role) → nothing badged', () => {
    const badges = businessPermissionBadgeKeys(GROUPS, subs('ecommerce'), roles(...ECOMMERCE_FULL_ROW12));
    for (const href of ECOMMERCE_LEAVES) expect(badges.has(href), href).toBe(false);
    expect(badges.has('ecommerce')).toBe(false);
  });

  it('no ecommerce role on a subscribed tenant → every ecommerce leaf AND the parent are badged', () => {
    expect(ECOMMERCE_LEAVES.length).toBe(9);
    const badges = businessPermissionBadgeKeys(GROUPS, subs('ecommerce'), roles());
    for (const href of ECOMMERCE_LEAVES) expect(badges.has(href), href).toBe(true);
    expect(badges.has('ecommerce')).toBe(true);
  });

  it('`ECOMMERCE_OPERATOR` alone (the derived entry role) opens no business API → still badged', () => {
    const badges = businessPermissionBadgeKeys(GROUPS, subs('ecommerce'), roles('ECOMMERCE_OPERATOR'));
    for (const href of ECOMMERCE_LEAVES) expect(badges.has(href), href).toBe(true);
  });

  it('read set (`ecommerce-read`) → no badge', () => {
    const badges = businessPermissionBadgeKeys(GROUPS, subs('ecommerce'), roles(...ECOMMERCE_READ_SET));
    for (const href of ECOMMERCE_LEAVES) expect(badges.has(href), href).toBe(false);
  });

  it('order set only → 개요 · 주문 open; the other 7 badged; parent not badged', () => {
    const badges = businessPermissionBadgeKeys(GROUPS, subs('ecommerce'), roles(...ECOMMERCE_ORDER_SET));
    expect(badges.has('/ecommerce')).toBe(false);
    expect(badges.has('/ecommerce/orders')).toBe(false);
    for (const href of [
      '/ecommerce/products', '/ecommerce/shippings', '/ecommerce/promotions', '/ecommerce/users',
      '/ecommerce/sellers', '/ecommerce/settlements', '/ecommerce/notifications/templates',
    ]) {
      expect(badges.has(href), href).toBe(true);
    }
    expect(badges.has('ecommerce')).toBe(false);
  });

  it('product set → 상품 and 판매자 open (sellers are the product area, Q1)', () => {
    const badges = businessPermissionBadgeKeys(
      GROUPS,
      subs('ecommerce'),
      roles('ECOMMERCE_PRODUCT_READ', 'ECOMMERCE_PRODUCT_WRITE'),
    );
    expect(badges.has('/ecommerce/products')).toBe(false);
    expect(badges.has('/ecommerce/sellers')).toBe(false);
    expect(badges.has('/ecommerce/users')).toBe(true);
  });

  it('a set without MEMBER_READ → 회원 badged (members are read-only — no WRITE role opens them)', () => {
    const allButMember = ECOMMERCE_CATALOG.filter((r) => r !== 'ECOMMERCE_MEMBER_READ');
    const badges = businessPermissionBadgeKeys(GROUPS, subs('ecommerce'), roles(...allButMember));
    expect(badges.has('/ecommerce/users')).toBe(true);
    expect(badges.has('/ecommerce/orders')).toBe(false);
  });

  it('settlement set only → 정산 open; 개요 badged (the overview reads no settlement)', () => {
    const badges = businessPermissionBadgeKeys(
      GROUPS,
      subs('ecommerce'),
      roles('ECOMMERCE_SETTLEMENT_READ', 'ECOMMERCE_SETTLEMENT_WRITE'),
    );
    expect(badges.has('/ecommerce/settlements')).toBe(false);
    expect(badges.has('/ecommerce')).toBe(true);
  });

  it('not subscribed → no «업무 권한 없음» (the subscription signal wins)', () => {
    const badges = businessPermissionBadgeKeys(GROUPS, subs('wms'), roles());
    for (const href of ECOMMERCE_LEAVES) expect(badges.has(href)).toBe(false);
    expect(badges.has('ecommerce')).toBe(false);
  });

  it('fail-open: row 12 unknown → no ecommerce badge', () => {
    expect(businessPermissionBadgeKeys(GROUPS, subs('ecommerce'), undefined).size).toBe(0);
  });
});

describe('subscription-only domains never get the signal (Finance)', () => {
  it('no role at all on a finance-subscribed tenant → no badge', () => {
    const badges = businessPermissionBadgeKeys(GROUPS, subs('finance'), roles());
    expect(badges.size).toBe(0);
  });
});

const ERP_LEAVES = ['/erp', '/erp/masters', '/erp/orgview', '/erp/approval', '/erp/delegation'];
/** System presets (V0057). */
const ERP_READ_SET = ['ERP_MASTERDATA_READ', 'ERP_APPROVAL_READ'];
const ERP_MASTERDATA_SET = ['ERP_MASTERDATA_READ', 'ERP_MASTERDATA_WRITE'];
const ERP_APPROVAL_SET = ['ERP_APPROVAL_READ', 'ERP_APPROVAL_WRITE'];

/**
 * TASK-PC-FE-343 — ERP per area (server: TASK-MONO-793 — masterdata `RoleScopeAuthorizationAdapter`,
 * approval `ActorContext`, read-model `ReadAuthorizationGate`; «구독 합성 READ 는 없다»).
 */
describe('TASK-PC-FE-343 — ERP menus follow the ERP business roles', () => {
  it('legacy-compat row 12 (the 4 roles, no entry role) → nothing badged', () => {
    const badges = businessPermissionBadgeKeys(GROUPS, subs('erp'), roles(...ERP_CATALOG));
    for (const href of ERP_LEAVES) expect(badges.has(href), href).toBe(false);
    expect(badges.has('erp')).toBe(false);
  });

  it('ERP set 0 on an erp-subscribed tenant → every ERP leaf AND the ERP parent badged', () => {
    const badges = businessPermissionBadgeKeys(GROUPS, subs('erp'), roles());
    for (const href of ERP_LEAVES) expect(badges.has(href), href).toBe(true);
    expect(badges.has('erp')).toBe(true);
    expect(badges.has('/erp/guide')).toBe(false);
  });

  it('`ERP_OPERATOR` alone (derived entry role) opens no business API → still badged', () => {
    const badges = businessPermissionBadgeKeys(GROUPS, subs('erp'), roles('ERP_OPERATOR'));
    for (const href of ERP_LEAVES) expect(badges.has(href), href).toBe(true);
  });

  it('read set (`erp-read`) → no badge', () => {
    const badges = businessPermissionBadgeKeys(GROUPS, subs('erp'), roles(...ERP_READ_SET));
    for (const href of ERP_LEAVES) expect(badges.has(href), href).toBe(false);
  });

  it('approval set only → 기준정보 · 조직 조회 badged; 결재 · 위임 · 개요 open; parent not badged', () => {
    const badges = businessPermissionBadgeKeys(GROUPS, subs('erp'), roles(...ERP_APPROVAL_SET));
    expect(badges.has('/erp/masters')).toBe(true);
    expect(badges.has('/erp/orgview')).toBe(true);
    expect(badges.has('/erp/approval')).toBe(false);
    expect(badges.has('/erp/delegation')).toBe(false);
    expect(badges.has('/erp')).toBe(false);
    expect(badges.has('erp')).toBe(false);
  });

  it('masterdata set only → 결재 · 위임 badged; 기준정보 · 조직 조회 · 개요 open', () => {
    const badges = businessPermissionBadgeKeys(GROUPS, subs('erp'), roles(...ERP_MASTERDATA_SET));
    expect(badges.has('/erp/approval')).toBe(true);
    expect(badges.has('/erp/delegation')).toBe(true);
    expect(badges.has('/erp/masters')).toBe(false);
    expect(badges.has('/erp/orgview')).toBe(false);
    expect(badges.has('/erp')).toBe(false);
  });

  it('erp not subscribed → «구독 필요» only, never «업무 권한 없음»', () => {
    const badges = businessPermissionBadgeKeys(GROUPS, subs('wms'), roles());
    for (const href of ERP_LEAVES) expect(badges.has(href)).toBe(false);
    expect(badges.has('erp')).toBe(false);
  });

  it('fail-open: row 12 unknown → no ERP badge', () => {
    expect(businessPermissionBadgeKeys(GROUPS, subs('erp'), undefined).size).toBe(0);
  });
});

/**
 * TASK-PC-FE-342 AC-2 — SCM per menu (server: TASK-MONO-792 `SecurityConfig` × 4, live 28th
 * window: `scm-read` → GETs 200 · PO create / policy PUT 403; WMS set only → scm PO 403).
 */
describe('TASK-PC-FE-342 — SCM menus follow the SCM business roles', () => {
  it('SCM set 0 on an scm-subscribed tenant → every SCM leaf AND the SCM parent are badged', () => {
    const badges = businessPermissionBadgeKeys(GROUPS, subs('scm'), roles());
    for (const href of SCM_LEAVES) expect(badges.has(href), href).toBe(true);
    expect(badges.has('scm')).toBe(true);
  });

  it('the guide is never badged (static — no server read)', () => {
    const badges = businessPermissionBadgeKeys(GROUPS, subs('scm'), roles());
    expect(badges.has('/scm/guide')).toBe(false);
  });

  it('`SCM_OPERATOR` alone (the derived entry role) opens no business API → still badged', () => {
    const badges = businessPermissionBadgeKeys(GROUPS, subs('scm'), roles('SCM_OPERATOR'));
    for (const href of SCM_LEAVES) expect(badges.has(href), href).toBe(true);
  });

  it('read set (`scm-read`) → no SCM badge', () => {
    const badges = businessPermissionBadgeKeys(GROUPS, subs('scm'), roles(...SCM_READ_SET));
    for (const href of SCM_LEAVES) expect(badges.has(href), href).toBe(false);
    expect(badges.has('scm')).toBe(false);
  });

  it('read set → 승인 · 기각 (보충 계획) and 정책 · 매핑 저장 (설정) hidden', () => {
    const r = roles(...SCM_READ_SET);
    expect(canWriteOn('/scm/replenishment', r)).toBe(false);
    expect(canWriteOn('/scm/config', r)).toBe(false);
  });

  it('demand-planning set only → 조달 · 재고 · 개요 badged; 보충 계획 · 설정 open; parent not badged', () => {
    const badges = businessPermissionBadgeKeys(
      GROUPS,
      subs('scm'),
      roles(...SCM_DEMAND_PLANNING_SET),
    );
    expect(badges.has('/scm/procurement')).toBe(true);
    expect(badges.has('/scm/inventory')).toBe(true);
    expect(badges.has('/scm')).toBe(true);
    expect(badges.has('/scm/replenishment')).toBe(false);
    expect(badges.has('/scm/config')).toBe(false);
    expect(badges.has('scm')).toBe(false);
  });

  it('demand-planning set only → 승인 · 기각 and 저장 shown (approve\'s draft PO accepts DP_WRITE, Q2)', () => {
    const r = roles(...SCM_DEMAND_PLANNING_SET);
    expect(canWriteOn('/scm/replenishment', r)).toBe(true);
    expect(canWriteOn('/scm/config', r)).toBe(true);
  });

  it('개요 opens on either card\'s family — inventory-visibility READ alone → no badge', () => {
    const badges = businessPermissionBadgeKeys(
      GROUPS,
      subs('scm'),
      roles('SCM_INVENTORY_VISIBILITY_READ'),
    );
    expect(badges.has('/scm')).toBe(false);
    expect(badges.has('/scm/inventory')).toBe(false);
    expect(badges.has('/scm/procurement')).toBe(true);
  });

  it('scm not subscribed → no «업무 권한 없음» on SCM (the subscription signal wins)', () => {
    const badges = businessPermissionBadgeKeys(GROUPS, subs('wms'), roles());
    for (const href of SCM_LEAVES) expect(badges.has(href), href).toBe(false);
    expect(badges.has('scm')).toBe(false);
  });

  it('fail-open: row 12 unknown → no SCM badge, SCM buttons shown', () => {
    expect(businessPermissionBadgeKeys(GROUPS, subs('scm'), undefined).size).toBe(0);
    expect(canWriteOn('/scm/replenishment', undefined)).toBe(true);
    expect(canWriteOn('/scm/config', undefined)).toBe(true);
  });
});

describe('TASK-PC-FE-342 — WMS 출고 «발송 재시도» = SCM_LOGISTICS_WRITE (button-level, Q3 strict)', () => {
  it('WMS outbound set only → 발송 재시도 hidden, the outbound buttons still shown', () => {
    const r = roles(...WMS_OUTBOUND_SET);
    expect(canWriteOn('/wms/outbound', r, 'retry-dispatch')).toBe(false);
    expect(canWriteOn('/wms/outbound', r)).toBe(true);
  });

  it('SCM_LOGISTICS_READ alone (dispatch lookup only) → hidden', () => {
    expect(canWriteOn('/wms/outbound', roles('OUTBOUND_READ', 'SCM_LOGISTICS_READ'), 'retry-dispatch')).toBe(false);
  });

  it('SCM_LOGISTICS_WRITE → shown even with only an outbound read set (the two servers are separate)', () => {
    const r = roles('OUTBOUND_READ', 'SCM_LOGISTICS_WRITE');
    expect(canWriteOn('/wms/outbound', r, 'retry-dispatch')).toBe(true);
    expect(canWriteOn('/wms/outbound', r)).toBe(false);
  });

  it('fail-open: row 12 unknown → 발송 재시도 shown', () => {
    expect(canWriteOn('/wms/outbound', undefined, 'retry-dispatch')).toBe(true);
  });

  it('an undeclared button falls back to the screen\'s writeRequires', () => {
    expect(canWriteOn('/wms/outbound', roles('OUTBOUND_WRITE'), 'no-such-button')).toBe(true);
    expect(canWriteOn('/wms/outbound', roles('SCM_LOGISTICS_WRITE'), 'no-such-button')).toBe(false);
  });
});

describe('fail-open — an outage never reads as «no permission»', () => {
  it('row 12 failed (undefined roles) → no badge', () => {
    expect(businessPermissionBadgeKeys(GROUPS, subs('wms', 'ecommerce'), undefined).size).toBe(0);
  });

  it('no active tenant / registry failed (undefined subscriptions) → no badge', () => {
    expect(businessPermissionBadgeKeys(GROUPS, undefined, roles()).size).toBe(0);
  });
});

describe('businessRolesWithin — row 12 ∩ subscribed domains (= the assumed token roles after P3)', () => {
  it('drops roles of an unsubscribed domain', () => {
    const out = businessRolesWithin(
      [
        { domainKey: 'wms', role: 'INVENTORY_READ' },
        { domainKey: 'scm', role: 'SCM_OPERATOR' },
      ],
      subs('wms'),
    );
    expect(out && [...out]).toEqual(['INVENTORY_READ']);
  });

  it('row 12 failed (null) or no subscription data → undefined (don\'t know)', () => {
    expect(businessRolesWithin(null, subs('wms'))).toBeUndefined();
    expect(businessRolesWithin([{ domainKey: 'wms', role: 'X' }], undefined)).toBeUndefined();
  });

  it('a subscribed tenant with no rows → an EMPTY set (known: no business roles)', () => {
    const out = businessRolesWithin([], subs('wms'));
    expect(out).toBeDefined();
    expect(out!.size).toBe(0);
  });
});

describe('AC-2 — canWriteOn (per-screen writeRequires)', () => {
  it('/wms/outbound: OUTBOUND_READ only (read set) → hide; OUTBOUND_WRITE → show', () => {
    expect(canWriteOn('/wms/outbound', roles('OUTBOUND_READ'))).toBe(false);
    expect(canWriteOn('/wms/outbound', roles('OUTBOUND_READ', 'OUTBOUND_WRITE'))).toBe(true);
  });

  it('/wms/outbound: an INVENTORY_WRITE holder is not READ-only but still cannot write outbound → hide', () => {
    expect(canWriteOn('/wms/outbound', roles('INVENTORY_READ', 'INVENTORY_WRITE'))).toBe(false);
  });

  it('/wms (alert acknowledge): WMS_OPERATOR → show; read set → hide', () => {
    expect(canWriteOn('/wms', roles('WMS_OPERATOR'))).toBe(true);
    expect(canWriteOn('/wms', roles('INVENTORY_READ'))).toBe(false);
  });

  it('fail-open: unknown roles (undefined) → show', () => {
    expect(canWriteOn('/wms/outbound', undefined)).toBe(true);
  });

  it('a screen with no writeRequires → never hidden', () => {
    expect(canWriteOn('/wms/inventory', roles())).toBe(true);
  });
});

describe('ledger — the declarations match the measurement', () => {
  const gateOf = (href: string) => PERMISSION_MAP.find((r) => r.href === href)?.gate;

  it('/wms/outbound declares OUTBOUND_READ · OUTBOUND_WRITE (read) and OUTBOUND_WRITE (write)', () => {
    const g = gateOf('/wms/outbound');
    expect(g?.kind).toBe('domain');
    if (g?.kind !== 'domain') return;
    expect([...(g.serverRequires ?? [])]).toEqual(['OUTBOUND_READ', 'OUTBOUND_WRITE']);
    expect([...(g.writeRequires ?? [])]).toEqual(['OUTBOUND_WRITE']);
  });

  it('/wms declares writeRequires WMS_OPERATOR and no serverRequires', () => {
    const g = gateOf('/wms');
    if (g?.kind !== 'domain') throw new Error('not a domain gate');
    expect(g.serverRequires).toBeUndefined();
    expect([...(g.writeRequires ?? [])]).toEqual(['WMS_OPERATOR']);
  });

  it('subscription-only WMS leaves declare no serverRequires', () => {
    for (const href of WMS_SUBSCRIPTION_ONLY) {
      const g = gateOf(href);
      if (g?.kind !== 'domain') throw new Error(`${href} not a domain gate`);
      expect(g.serverRequires).toBeUndefined();
    }
  });

  it('every declared role is a catalog role (a typo would badge forever)', () => {
    for (const row of PERMISSION_MAP) {
      if (row.gate.kind !== 'domain') continue;
      const buttonRoles = Object.values(row.gate.buttonWriteRequires ?? {}).flat();
      for (const r of [
        ...(row.gate.serverRequires ?? []),
        ...(row.gate.writeRequires ?? []),
        ...buttonRoles,
      ]) {
        expect(CATALOG_ROLES.has(r), `${row.href}: ${r}`).toBe(true);
      }
    }
  });

  it('TASK-PC-FE-342 — SCM rows declare exactly what the four SecurityConfigs enforce', () => {
    const expected: Record<string, { server: string[]; write?: string[] }> = {
      '/scm': {
        server: [
          'SCM_PROCUREMENT_READ', 'SCM_PROCUREMENT_WRITE',
          'SCM_INVENTORY_VISIBILITY_READ', 'SCM_INVENTORY_VISIBILITY_WRITE',
        ],
      },
      '/scm/procurement': { server: ['SCM_PROCUREMENT_READ', 'SCM_PROCUREMENT_WRITE'] },
      '/scm/inventory': {
        server: ['SCM_INVENTORY_VISIBILITY_READ', 'SCM_INVENTORY_VISIBILITY_WRITE'],
      },
      '/scm/replenishment': {
        server: ['SCM_DEMAND_PLANNING_READ', 'SCM_DEMAND_PLANNING_WRITE'],
        write: ['SCM_DEMAND_PLANNING_WRITE'],
      },
      '/scm/config': {
        server: ['SCM_DEMAND_PLANNING_READ', 'SCM_DEMAND_PLANNING_WRITE'],
        write: ['SCM_DEMAND_PLANNING_WRITE'],
      },
    };
    for (const [href, want] of Object.entries(expected)) {
      const g = gateOf(href);
      if (g?.kind !== 'domain') throw new Error(`${href} not a domain gate`);
      expect([...(g.serverRequires ?? [])], href).toEqual(want.server);
      if (want.write) expect([...(g.writeRequires ?? [])], href).toEqual(want.write);
      else expect(g.writeRequires, href).toBeUndefined();
    }
  });

  it('TASK-PC-FE-342 — SCM gate.roles = the 8 SCM catalog roles; `SCM_OPERATOR` declared nowhere', () => {
    for (const href of SCM_LEAVES) {
      const g = gateOf(href);
      if (g?.kind !== 'domain') throw new Error(`${href} not a domain gate`);
      expect([...g.roles].sort(), href).toEqual([...SCM_CATALOG].sort());
    }
    for (const row of PERMISSION_MAP) {
      if (row.gate.kind !== 'domain') continue;
      const declared = [
        ...(row.gate.serverRequires ?? []),
        ...(row.gate.writeRequires ?? []),
        ...Object.values(row.gate.buttonWriteRequires ?? {}).flat(),
      ];
      expect(declared.includes('SCM_OPERATOR'), row.href).toBe(false);
    }
  });

  it('TASK-PC-FE-345 — no declaration names an entry role (SCM · ERP · Finance · E-commerce _OPERATOR)', () => {
    for (const row of PERMISSION_MAP) {
      if (row.gate.kind !== 'domain') continue;
      const declared = [
        ...(row.gate.serverRequires ?? []),
        ...(row.gate.writeRequires ?? []),
        ...Object.values(row.gate.buttonWriteRequires ?? {}).flat(),
      ];
      for (const entry of ENTRY_ROLES) {
        expect(declared.includes(entry), `${row.href}: ${entry}`).toBe(false);
      }
    }
  });

  it('TASK-PC-FE-343 — ERP rows declare exactly what the ERP services enforce (no buttons yet → 347)', () => {
    const MD = ['ERP_MASTERDATA_READ', 'ERP_MASTERDATA_WRITE'];
    const AP = ['ERP_APPROVAL_READ', 'ERP_APPROVAL_WRITE'];
    const expected: Record<string, string[]> = {
      '/erp': [...MD, ...AP],
      '/erp/masters': MD,
      '/erp/orgview': MD,
      '/erp/approval': AP,
      '/erp/delegation': AP,
    };
    for (const [href, want] of Object.entries(expected)) {
      const g = gateOf(href);
      if (g?.kind !== 'domain') throw new Error(`${href} not a domain gate`);
      expect([...(g.serverRequires ?? [])].sort(), href).toEqual([...want].sort());
      expect([...g.roles].sort(), href).toEqual([...ERP_CATALOG].sort());
      expect(g.writeRequires, href).toBeUndefined();
    }
  });

  it('TASK-PC-FE-345 — E-commerce rows declare exactly what the area services enforce', () => {
    const rw = (area: string) => [`ECOMMERCE_${area}_READ`, `ECOMMERCE_${area}_WRITE`];
    const expected: Record<string, string[]> = {
      '/ecommerce': [
        ...rw('ORDER'), ...rw('PRODUCT'), 'ECOMMERCE_MEMBER_READ', ...rw('PROMOTION'),
        ...rw('SHIPPING'), ...rw('NOTIFICATION'),
      ],
      '/ecommerce/products': rw('PRODUCT'),
      '/ecommerce/sellers': rw('PRODUCT'),
      '/ecommerce/orders': rw('ORDER'),
      '/ecommerce/shippings': rw('SHIPPING'),
      '/ecommerce/promotions': rw('PROMOTION'),
      '/ecommerce/users': ['ECOMMERCE_MEMBER_READ'],
      '/ecommerce/settlements': rw('SETTLEMENT'),
      '/ecommerce/notifications/templates': rw('NOTIFICATION'),
    };
    expect(Object.keys(expected).sort()).toEqual([...ECOMMERCE_LEAVES].sort());
    for (const [href, want] of Object.entries(expected)) {
      const g = gateOf(href);
      if (g?.kind !== 'domain') throw new Error(`${href} not a domain gate`);
      expect([...(g.serverRequires ?? [])].sort(), href).toEqual([...want].sort());
      expect([...g.roles].sort(), href).toEqual([...ECOMMERCE_CATALOG].sort());
    }
  });

  it('TASK-PC-FE-342 — /wms/outbound declares «발송 재시도» = SCM_LOGISTICS_WRITE at button level', () => {
    const g = gateOf('/wms/outbound');
    if (g?.kind !== 'domain') throw new Error('not a domain gate');
    expect(g.buttonWriteRequires?.['retry-dispatch']).toEqual(['SCM_LOGISTICS_WRITE']);
    // the screen-level read/write rows are unchanged
    expect([...(g.writeRequires ?? [])]).toEqual(['OUTBOUND_WRITE']);
  });
});
