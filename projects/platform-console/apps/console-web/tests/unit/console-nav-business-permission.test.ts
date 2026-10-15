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
 *   - every ecommerce leaf → the ecommerce gateway needs ECOMMERCE_OPERATOR;
 *   - SCM · ERP · Finance reads → open on subscription alone.
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

/** The admin-service catalog (admin-api.md § 카탈로그 — 14 roles). */
const CATALOG_ROLES = new Set([
  'INVENTORY_READ', 'INVENTORY_WRITE', 'INBOUND_READ', 'INBOUND_WRITE', 'OUTBOUND_READ',
  'OUTBOUND_WRITE', 'MASTER_READ', 'WMS_OPERATOR', 'SCM_OPERATOR', 'ERP_OPERATOR',
  'FINANCE_OPERATOR', 'ECOMMERCE_OPERATOR', 'FAN_OPERATOR', 'MES_OPERATOR',
]);

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

describe('ecommerce — the gateway is the only gate (ECOMMERCE_OPERATOR)', () => {
  it('no ecommerce role on a subscribed tenant → every ecommerce leaf AND the parent are badged', () => {
    expect(ECOMMERCE_LEAVES.length).toBeGreaterThan(0);
    const badges = businessPermissionBadgeKeys(GROUPS, subs('ecommerce'), roles());
    for (const href of ECOMMERCE_LEAVES) expect(badges.has(href)).toBe(true);
    expect(badges.has('ecommerce')).toBe(true);
  });

  it('with ECOMMERCE_OPERATOR → nothing badged', () => {
    const badges = businessPermissionBadgeKeys(GROUPS, subs('ecommerce'), roles('ECOMMERCE_OPERATOR'));
    expect(badges.size).toBe(0);
  });

  it('not subscribed → no «업무 권한 없음» (the subscription signal wins)', () => {
    const badges = businessPermissionBadgeKeys(GROUPS, subs('wms'), roles());
    for (const href of ECOMMERCE_LEAVES) expect(badges.has(href)).toBe(false);
    expect(badges.has('ecommerce')).toBe(false);
  });
});

describe('subscription-only domains never get the signal (SCM · ERP · Finance)', () => {
  it('no role at all on a tenant subscribed to all three → no badge in those domains', () => {
    const badges = businessPermissionBadgeKeys(GROUPS, subs('scm', 'erp', 'finance'), roles());
    expect(badges.size).toBe(0);
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
      for (const r of [...(row.gate.serverRequires ?? []), ...(row.gate.writeRequires ?? [])]) {
        expect(CATALOG_ROLES.has(r), `${row.href}: ${r}`).toBe(true);
      }
    }
  });
});
