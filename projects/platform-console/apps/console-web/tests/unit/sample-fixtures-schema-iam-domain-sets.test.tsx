/**
 * 업무 권한 세트 · 테넌트 진입 정책 sample fixtures (TASK-PC-FE-382 — the last two
 * IAM `pending` ledger rows). Same conventions as `sample-fixtures-schema-iam.test.ts`:
 * every assertion goes THROUGH `sampleResponse` and parses with the SAME zod schema
 * the real screen uses.
 *
 * 🔴 Schema-only is not enough (the TASK-PC-FE-373 `alertId` ↔ `id` class): the console
 *    schema can be satisfied by a shape the PRODUCER never sends. So the field names and
 *    the catalogue/preset keys below are pinned against `admin-api.md` literally —
 *    `CONTRACT_PRESET_KEYS` is copied from § 시스템 프리셋 and `WIRE_*_KEYS` from the two
 *    wire-shape blocks, NOT derived from the fixture.
 */
import { describe, it, expect, afterEach } from 'vitest';
import { render, cleanup } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { sampleResponse } from '@/shared/sample/router';
import { SAMPLE_READ_ONLY } from '@/shared/sample/codes';
import { screenStatusFor } from '@/shared/sample/coverage';
import {
  DomainRoleListSchema,
  DomainPermissionSetListSchema,
  DomainPermissionSetSchema,
} from '@/features/domain-permission-sets/api/types';
import { EntryPolicySchema, EnrolmentSummarySchema } from '@/features/tenant-entry-policy/api/types';
import { EntryPolicySection } from '@/features/tenant-entry-policy/components/EntryPolicySection';
import { IAM_TENANTS } from '@/shared/sample/fixtures/iam';

function get(surface: string, path: string, method = 'GET'): Response {
  return sampleResponse({ core: 'iam', surface, method, path });
}

// --- the producer contract, copied literally (admin-api.md § 도메인 업무 권한 세트) ----

/** § Domain role wire shape. */
const WIRE_ROLE_KEYS = ['access', 'domainKey', 'fineGrained', 'label', 'role'];
/** § Domain permission set wire shape. */
const WIRE_SET_KEYS = [
  'createdAt',
  'description',
  'domains',
  'groupCount',
  'grantCount',
  'name',
  'readOnly',
  'roles',
  'setId',
  'systemKey',
  'tenantId',
  'updatedAt',
].sort();
/** § 시스템 프리셋 — every `systemKey` (the table incl. the V0056–V0059 additions). */
const CONTRACT_PRESET_KEYS = [
  'wms-inventory-read',
  'wms-inventory-operate',
  'wms-inbound-operate',
  'wms-outbound-operate',
  'wms-master-read',
  'wms-ops-actions',
  'wms-full',
  'scm-full',
  'erp-full',
  'finance-full',
  'ecommerce-full',
  'fan-full',
  'mes-full',
  'scm-read',
  'scm-procurement-operate',
  'scm-demand-planning-operate',
  'scm-logistics-operate',
  'scm-inventory-visibility-operate',
  'erp-read',
  'erp-masterdata-operate',
  'erp-approval-operate',
  'finance-read',
  'finance-account-operate',
  'finance-ledger-operate',
  'ecommerce-read',
  'ecommerce-order-operate',
  'ecommerce-product-operate',
  'ecommerce-settlement-operate',
  'ecommerce-promotion-operate',
  'ecommerce-shipping-operate',
  'ecommerce-notification-operate',
  'all-domains-full',
  'legacy-compat',
];

const sortedKeys = (o: object) => Object.keys(o).sort();

describe('domain_permission_sets — GET /api/admin/domain-roles', () => {
  it('parses with DomainRoleListSchema and carries exactly the producer wire keys', async () => {
    const res = get('domain_permission_sets', '/api/admin/domain-roles');
    expect(res.status).toBe(200);
    const raw = (await res.json()) as { items: object[] };
    expect(Object.keys(raw)).toEqual(['items']);
    const parsed = DomainRoleListSchema.parse(raw);
    expect(parsed.items.length).toBe(40); // the catalogue table has 40 rows
    for (const row of raw.items) expect(sortedKeys(row)).toEqual(WIRE_ROLE_KEYS);
  });

  it('uses the real catalogue keys: per-domain counts, fan/mes not fine-grained, no entry roles', async () => {
    const { items } = DomainRoleListSchema.parse(
      await get('domain_permission_sets', '/api/admin/domain-roles').json(),
    );
    const count = (d: string) => items.filter((r) => r.domainKey === d).length;
    expect({
      wms: count('wms'),
      scm: count('scm'),
      erp: count('erp'),
      finance: count('finance'),
      ecommerce: count('ecommerce'),
      fan: count('fan'),
      mes: count('mes'),
    }).toEqual({ wms: 8, scm: 8, erp: 4, finance: 5, ecommerce: 13, fan: 1, mes: 1 });
    expect(items.filter((r) => !r.fineGrained).map((r) => r.role)).toEqual(['FAN_OPERATOR', 'MES_OPERATOR']);
    // 진입 역할은 카탈로그에 없다 (배정 불가 — auth 가 파생).
    const keys = items.map((r) => r.role);
    for (const entry of ['SCM_OPERATOR', 'ERP_OPERATOR', 'FINANCE_OPERATOR', 'ECOMMERCE_OPERATOR']) {
      expect(keys).not.toContain(entry);
    }
    // 통제 역할의 `access` 는 WRITE (와이어 값은 READ · WRITE · OPERATE 셋뿐).
    expect(items.find((r) => r.role === 'FINANCE_LEDGER_CONTROL')?.access).toBe('WRITE');
    expect(items.find((r) => r.role === 'WMS_OPERATOR')?.access).toBe('OPERATE');
  });
});

describe('domain_permission_sets — GET /api/admin/domain-permission-sets', () => {
  async function list() {
    const res = get('domain_permission_sets', '/api/admin/domain-permission-sets');
    expect(res.status).toBe(200);
    const raw = (await res.json()) as { items: object[] };
    expect(Object.keys(raw)).toEqual(['items']);
    return { raw, parsed: DomainPermissionSetListSchema.parse(raw) };
  }

  it('parses, carries exactly the producer wire keys, presets first then tenant sets', async () => {
    const { raw, parsed } = await list();
    for (const row of raw.items) expect(sortedKeys(row)).toEqual(WIRE_SET_KEYS);
    const readOnlyFlags = parsed.items.map((s) => s.readOnly);
    const firstTenantSet = readOnlyFlags.indexOf(false);
    expect(firstTenantSet).toBeGreaterThan(0);
    // presets before tenant sets, never interleaved
    expect(readOnlyFlags.slice(0, firstTenantSet).every(Boolean)).toBe(true);
    expect(readOnlyFlags.slice(firstTenantSet).every((f) => f === false)).toBe(true);
  });

  it('presets: exactly the contract systemKeys, readOnly, tenantId null, fixed unique setIds', async () => {
    const { parsed } = await list();
    const presets = parsed.items.filter((s) => s.readOnly);
    expect(presets.map((s) => s.systemKey).sort()).toEqual([...CONTRACT_PRESET_KEYS].sort());
    for (const p of presets) {
      expect(p.tenantId).toBeNull();
      expect(p.setId).toMatch(/^00000083-0000-7000-8000-\d{12}$/);
    }
    expect(new Set(presets.map((s) => s.setId)).size).toBe(presets.length);
    // a spot check against the V0052 seed: the first preset's fixed id.
    expect(presets.find((s) => s.systemKey === 'wms-inventory-read')?.setId).toBe(
      '00000083-0000-7000-8000-000000000001',
    );
  });

  it('preset contents follow the contract (finance-ledger-operate has NO control role; full sets = whole domain)', async () => {
    const { parsed } = await list();
    const byKey = (k: string) => parsed.items.find((s) => s.systemKey === k)!;
    expect(byKey('wms-inventory-operate').roles.map((r) => r.role)).toEqual(['INVENTORY_READ', 'INVENTORY_WRITE']);
    expect(byKey('finance-ledger-operate').roles.map((r) => r.role)).toEqual([
      'FINANCE_LEDGER_READ',
      'FINANCE_LEDGER_WRITE',
    ]);
    expect(byKey('finance-full').roles.map((r) => r.role)).toContain('FINANCE_LEDGER_CONTROL');
    expect(byKey('ecommerce-read').roles).toHaveLength(7);
    expect(byKey('all-domains-full').roles).toHaveLength(40);
    expect(byKey('legacy-compat').roles).toEqual(byKey('all-domains-full').roles);
    expect(byKey('all-domains-full').domains).toEqual([
      'ecommerce',
      'erp',
      'fan',
      'finance',
      'mes',
      'scm',
      'wms',
    ]);
  });

  it('every set: roles are in catalogue order without duplicates; domains = the roles\' domainKeys, ascending', async () => {
    const { parsed } = await list();
    const catalogue = DomainRoleListSchema.parse(
      await get('domain_permission_sets', '/api/admin/domain-roles').json(),
    ).items;
    const order = new Map(catalogue.map((r, i) => [r.role, i]));
    for (const s of parsed.items) {
      expect(s.roles.length, s.setId).toBeGreaterThan(0);
      const idx = s.roles.map((r) => order.get(r.role));
      expect(idx.every((i) => i !== undefined), `${s.setId} uses a role not in the catalogue`).toBe(true);
      expect(idx).toEqual([...idx].sort((a, b) => (a as number) - (b as number)));
      expect(new Set(s.roles.map((r) => r.role)).size).toBe(s.roles.length);
      // the role-ref's domainKey agrees with the catalogue
      for (const r of s.roles) expect(catalogue.find((c) => c.role === r.role)?.domainKey).toBe(r.domainKey);
      expect(s.domains).toEqual([...new Set(s.roles.map((r) => r.domainKey))].sort());
    }
  });

  it('tenant sets: owned by the sample tenant, not read-only, systemKey null, readable by the 목록 screen', async () => {
    const { parsed } = await list();
    const mine = parsed.items.filter((s) => !s.readOnly);
    expect(mine.length).toBeGreaterThanOrEqual(1);
    for (const s of mine) {
      expect(s.tenantId).toBe('sample');
      expect(s.systemKey).toBeNull();
      expect(s.name).toMatch(/\(샘플\)$/);
    }
  });

  it('detail: every listed setId resolves to the same row', async () => {
    const { parsed } = await list();
    for (const s of parsed.items) {
      const res = get('domain_permission_sets', `/api/admin/domain-permission-sets/${encodeURIComponent(s.setId)}`);
      expect(res.status, s.setId).toBe(200);
      expect(DomainPermissionSetSchema.parse(await res.json())).toEqual(s);
    }
  });

  it('detail: an unknown setId 404s with the producer code in the FLAT envelope', async () => {
    const res = get('domain_permission_sets', '/api/admin/domain-permission-sets/no-such-set');
    expect(res.status).toBe(404);
    const body = (await res.json()) as { code?: string; message?: string; timestamp?: string };
    expect(body.code).toBe('DOMAIN_PERMISSION_SET_NOT_FOUND');
    expect(typeof body.message).toBe('string');
    expect(typeof body.timestamp).toBe('string');
  });

  it('a path this surface has no fixture for stays 503 (never a silent 200)', () => {
    expect(get('domain_permission_sets', '/api/admin/domain-permission-sets/x/y').status).toBe(503);
  });

  it('writes are refused with 403 SAMPLE_READ_ONLY (ADR-074 R1ⓐ) — no new rule', async () => {
    for (const [method, path] of [
      ['POST', '/api/admin/domain-permission-sets'],
      ['PATCH', '/api/admin/domain-permission-sets/0192f3a0-0000-7000-8000-00000000d001'],
      ['DELETE', '/api/admin/domain-permission-sets/0192f3a0-0000-7000-8000-00000000d001'],
    ] as const) {
      const res = get('domain_permission_sets', path, method);
      expect(res.status, `${method} ${path}`).toBe(403);
      expect(((await res.json()) as { code: string }).code).toBe(SAMPLE_READ_ONLY);
    }
  });
});

describe('tenant_entry_policy — GET /api/admin/tenants/{tenantId}/entry-policy[/enrolment-summary]', () => {
  const POLICY_KEYS = ['requireMfa', 'tenantId', 'updatedAt', 'updatedBy'];
  const SUMMARY_KEYS = ['enrolled', 'notEnrolled', 'operators', 'tenantId', 'unlinked'];

  it('every registry tenant has a policy that parses, with exactly the producer keys', async () => {
    for (const t of IAM_TENANTS) {
      const res = get('tenant_entry_policy', `/api/admin/tenants/${t.tenantId}/entry-policy`);
      expect(res.status, t.tenantId).toBe(200);
      const raw = await res.json();
      expect(sortedKeys(raw)).toEqual(POLICY_KEYS);
      const p = EntryPolicySchema.parse(raw);
      expect(p.tenantId).toBe(t.tenantId);
      // stamps move together: `null` = never set, otherwise both present
      expect(p.updatedAt === null).toBe(p.updatedBy === null);
    }
  });

  it('the policies are not all the same (on / never set / turned off again) so the control shows more than one state', async () => {
    const all = await Promise.all(
      IAM_TENANTS.map(async (t) =>
        EntryPolicySchema.parse(
          await get('tenant_entry_policy', `/api/admin/tenants/${t.tenantId}/entry-policy`).json(),
        ),
      ),
    );
    expect(all.some((p) => p.requireMfa)).toBe(true);
    expect(all.some((p) => !p.requireMfa && p.updatedAt === null)).toBe(true);
    expect(all.some((p) => !p.requireMfa && p.updatedAt !== null)).toBe(true);
  });

  it('the enrolment summary parses and obeys operators = enrolled + notEnrolled + unlinked', async () => {
    for (const t of IAM_TENANTS) {
      const res = get('tenant_entry_policy', `/api/admin/tenants/${t.tenantId}/entry-policy/enrolment-summary`);
      expect(res.status, t.tenantId).toBe(200);
      const raw = await res.json();
      expect(sortedKeys(raw)).toEqual(SUMMARY_KEYS);
      const s = EnrolmentSummarySchema.parse(raw);
      expect(s.tenantId).toBe(t.tenantId);
      expect(s.operators).toBe(s.enrolled + s.notEnrolled + s.unlinked);
      expect(s.operators).toBeGreaterThan(0);
    }
  });

  it('the summary agrees with the 운영자 screen\'s seed (ACTIVE operators, totpEnrolled)', async () => {
    const ops = (await get('operators', '/api/admin/operators?page=0&size=100').json()) as {
      content: { status: string; totpEnrolled: boolean }[];
    };
    const active = ops.content.filter((o) => o.status === 'ACTIVE');
    const s = EnrolmentSummarySchema.parse(
      await get('tenant_entry_policy', '/api/admin/tenants/sample/entry-policy/enrolment-summary').json(),
    );
    expect(s.operators).toBe(active.length);
    expect(s.enrolled).toBe(active.filter((o) => o.totpEnrolled).length);
  });

  it('a tenantId with no policy row is NOT a 404 (contract: 꺼짐은 정상 상태) — 200 with the exact default body', async () => {
    const res = get('tenant_entry_policy', '/api/admin/tenants/no-such-tenant/entry-policy');
    expect(res.status).toBe(200);
    const raw = await res.json();
    expect(raw).toEqual({ tenantId: 'no-such-tenant', requireMfa: false, updatedAt: null, updatedBy: null });
    EntryPolicySchema.parse(raw);
  });

  it('the summary of such a tenant is 200 with zero counts (contract silent; consistent with the policy endpoint)', async () => {
    const res = get('tenant_entry_policy', '/api/admin/tenants/no-such-tenant/entry-policy/enrolment-summary');
    expect(res.status).toBe(200);
    const raw = await res.json();
    expect(raw).toEqual({ tenantId: 'no-such-tenant', operators: 0, enrolled: 0, notEnrolled: 0, unlinked: 0 });
    EnrolmentSummarySchema.parse(raw);
  });

  it('PUT is refused with 403 SAMPLE_READ_ONLY (the toggle never reaches a backend)', async () => {
    const res = get('tenant_entry_policy', '/api/admin/tenants/sample/entry-policy', 'PUT');
    expect(res.status).toBe(403);
    expect(((await res.json()) as { code: string }).code).toBe(SAMPLE_READ_ONLY);
  });

  it('a path this surface has no fixture for stays 503', () => {
    expect(get('tenant_entry_policy', '/api/admin/tenants/sample/entry-policy/other').status).toBe(503);
  });
});

describe('AC-3 — a sample visitor no longer gets the «준비 중» note on these screens', () => {
  afterEach(() => cleanup());

  it('the screen ledger says ready (the shell\'s SampleScreenNotice only renders for `pending`)', () => {
    for (const route of [
      '/domain-permission-sets',
      '/security-settings',
      '/tenants/sample',
      '/operators',
      '/permission-sets',
    ]) {
      expect(screenStatusFor(route), route).toBe('ready');
    }
  });

  it('the entry-policy control renders the panel (not its degraded note) from the sample policy', async () => {
    const policy = EntryPolicySchema.parse(
      await get('tenant_entry_policy', '/api/admin/tenants/sample/entry-policy').json(),
    );
    const { container } = render(
      <QueryClientProvider client={new QueryClient()}>
        <EntryPolicySection
          tenantId="sample"
          state={{
            policy,
            noTenant: false,
            permissionError: null,
            degraded: false,
            selfHasSecondFactor: null,
          }}
        />
      </QueryClientProvider>,
    );
    expect(container.querySelector('[data-testid="entry-policy-panel"]')).not.toBeNull();
    expect(container.querySelector('[data-testid="entry-policy-status"]')?.getAttribute('data-require-mfa')).toBe(
      'true',
    );
    expect(container.querySelector('[data-testid="entry-policy-degraded"]')).toBeNull();
    expect(container.textContent ?? '').not.toContain('준비 중');
  });
});
