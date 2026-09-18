/**
 * Every `ready` fixture parses with the SAME schema the real screen uses
 * (TASK-PC-FE-282 AC-10 / Failure Scenario «픽스처가 파서 스키마를 어김»).
 *
 * 🔴 A fixture that broke its schema would render as the section-degrade state —
 *    indistinguishable from «the sample is broken» and from «not ready». So the
 *    parse is asserted here, and so is the narrower per-card summary parse: an
 *    overview card whose `data` fails its narrower schema renders «—», which
 *    would look like a working screen with no numbers.
 */
import { describe, it, expect } from 'vitest';
import { RegistryResponseSchema } from '@/shared/api/registry-types';
import {
  OperatorOverviewSchema,
  GapDataSchema,
  WmsDataSchema,
  ScmDataSchema,
  FinanceDataSchema,
  ErpDataSchema,
  EcommerceDataSchema,
} from '@/features/operator-overview/api/operator-overview-types';
import { DomainHealthSchema } from '@/features/domain-health/api/types';
import { NotificationInboxResponseSchema } from '@/features/notifications/api/notification-types';
import { SAMPLE_REGISTRY } from '@/shared/sample/fixtures/registry';
import {
  SAMPLE_OPERATOR_OVERVIEW,
  SAMPLE_DOMAIN_HEALTH,
  SAMPLE_NOTIFICATION_INBOX,
} from '@/shared/sample/fixtures/dashboards';
import { SAMPLE_TENANT_ID } from '@/shared/sample/codes';

describe('sample fixtures parse with the production schemas', () => {
  it('registry', () => {
    const parsed = RegistryResponseSchema.parse(SAMPLE_REGISTRY);
    expect(parsed.products.map((p) => p.productKey).sort()).toEqual(
      ['ecommerce', 'erp', 'finance', 'iam', 'scm', 'wms'],
    );
  });

  it('AC-12 — every product is available and lists the sample tenant', () => {
    for (const p of RegistryResponseSchema.parse(SAMPLE_REGISTRY).products) {
      expect(p.available).toBe(true);
      expect(p.tenants).toEqual([SAMPLE_TENANT_ID]);
    }
  });

  it('operator overview', () => {
    const parsed = OperatorOverviewSchema.parse(SAMPLE_OPERATOR_OVERVIEW);
    expect(parsed.cards.every((c) => c.status === 'ok')).toBe(true);
  });

  it('operator overview — each card passes the narrower summary schema with real numbers', () => {
    const byDomain = Object.fromEntries(
      OperatorOverviewSchema.parse(SAMPLE_OPERATOR_OVERVIEW).cards.map((c) => [
        c.domain,
        c.status === 'ok' ? c.data : undefined,
      ]),
    );
    // TASK-PC-FE-295: the fields below are the ones each card actually reads
    // out of the PRODUCER's body. 🔴 `.parse()` succeeding proves almost
    // nothing on its own here — every one of these schemas is optional +
    // passthrough, so it also accepts a body with none of these keys (that is
    // exactly how the finance / wms / scm mismatch survived). The assertions
    // are therefore on the VALUES, and the render-side proof is the AC-7
    // census in `features/operator-overview/leg-body-contract.test.tsx`.
    expect(GapDataSchema.parse(byDomain.iam).totalElements).toBeTypeOf('number');
    expect(WmsDataSchema.parse(byDomain.wms).page?.totalElements).toBeTypeOf('number');
    expect(ScmDataSchema.parse(byDomain.scm).data?.totalElements).toBeTypeOf('number');
    expect(FinanceDataSchema.parse(byDomain.finance).data?.length).toBeGreaterThan(0);
    expect(FinanceDataSchema.parse(byDomain.finance).data?.[0].ledger).toBeTypeOf('string');
    expect(ErpDataSchema.parse(byDomain.erp).meta?.totalElements).toBeTypeOf('number');
    expect(EcommerceDataSchema.parse(byDomain.ecommerce).totalElements).toBeTypeOf('number');
  });

  it('domain health', () => {
    const parsed = DomainHealthSchema.parse(SAMPLE_DOMAIN_HEALTH);
    expect(parsed.cards).toHaveLength(6);
  });

  it('notification inbox', () => {
    const parsed = NotificationInboxResponseSchema.parse(SAMPLE_NOTIFICATION_INBOX);
    expect(parsed.items.length).toBeGreaterThan(0);
    expect(parsed.items.some((n) => !n.read)).toBe(true);
  });
});
