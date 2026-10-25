import { describe, it, expect, vi } from 'vitest';

/**
 * TASK-PC-FE-372 — the home headline legs (`shared/composition/operator-overview-headline.ts`) restate each
 * domain's «실적» metrics path, because `shared/` may not import `features/` (architecture.md § Forbidden
 * Dependencies). Tests may import both, so THIS file is the pin: each leg's path must equal the path the
 * domain's own fetcher uses — read from the fetcher module's exported constant, not retyped here. A fetcher
 * path change without the matching headline change turns this red (before TASK-PC-FE-372 the composition's
 * comment claimed such a test existed; the only test routed by substring and never read a fetcher).
 *
 * 🔵 The **base** is pinned by the table below, not by the fetchers (each domain's base lives inside its own
 * client helper, not as a constant). That half is a third copy and is weaker; every row names where the
 * fetcher's base comes from so a reviewer can check it.
 */

const ENV = {
  IAM_ADMIN_API_BASE: 'http://iam.test',
  WMS_ADMIN_BASE_URL: 'http://wms.test/api/v1/admin',
  SCM_GATEWAY_BASE_URL: 'http://scm.test',
  FINANCE_BASE_URL: 'http://finance.test',
  LEDGER_BASE_URL: 'http://ledger.test',
  ERP_BASE_URL: 'http://erp.test',
  ECOMMERCE_ADMIN_BASE_URL: 'http://ecommerce.test/api/admin',
};
vi.mock('@/shared/config/env', () => ({ getServerEnv: () => ENV }));

import { operatorOverviewHeadlineLegs } from '@/shared/composition/operator-overview-headline';
import { IAM_METRICS_PATH } from '@/features/iam-overview/api/iam-metrics-api';
import { WMS_METRICS_PATH } from '@/features/wms-ops/api/wms-metrics-api';
import { PO_METRICS_PATH } from '@/features/scm-ops/api/po-metrics-api';
import { FINANCE_METRICS_PATH } from '@/features/finance-overview/api/finance-metrics-api';
import { APPROVAL_METRICS_PATH } from '@/features/erp-ops/api/approval-metrics-api';
import { ORDER_METRICS_PATH } from '@/features/ecommerce-ops/api/orders-api';

/** domain → [the fetcher's exported path, the base env key its client uses (source)]. */
const FETCHERS: Record<string, { path: string; base: keyof typeof ENV; baseSource: string }> = {
  iam: { path: IAM_METRICS_PATH, base: 'IAM_ADMIN_API_BASE', baseSource: 'shared/api/iam-gateway.ts:167' },
  wms: { path: WMS_METRICS_PATH, base: 'WMS_ADMIN_BASE_URL', baseSource: 'features/wms-ops/api/wms-client.ts:106' },
  scm: { path: PO_METRICS_PATH, base: 'SCM_GATEWAY_BASE_URL', baseSource: 'shared/api/scm-gateway.ts:73-75' },
  finance: { path: FINANCE_METRICS_PATH, base: 'FINANCE_BASE_URL', baseSource: 'shared/api/finance-accounts-read.ts:97,108' },
  erp: { path: APPROVAL_METRICS_PATH, base: 'ERP_BASE_URL', baseSource: 'features/erp-ops/api/approval-call.ts:81' },
  ecommerce: { path: ORDER_METRICS_PATH, base: 'ECOMMERCE_ADMIN_BASE_URL', baseSource: 'features/ecommerce-ops/api/orders-api.ts (getOrderMetrics base)' },
};

const CREDS = { tenant: 'acme', operatorToken: 'op', domainFacingToken: 'dom', requestId: 'r-1' };

describe('home headline legs — paths pinned to the domain fetchers (TASK-PC-FE-372)', () => {
  it('🔴 every leg asks the same base + path as that domain\'s «실적» fetcher, with period=week', async () => {
    const legs = await operatorOverviewHeadlineLegs(CREDS);
    expect(legs.map((l) => l.domain).sort()).toEqual(Object.keys(FETCHERS).sort());

    for (const leg of legs) {
      const f = FETCHERS[leg.domain];
      // every headline leg must be a data leg (it asks a URL); a pre-decided leg here would ask nothing
      expect('url' in leg, `${leg.domain} is a data leg`).toBe(true);
      if (!('url' in leg)) continue;
      const url = new URL(leg.url);
      const expected = new URL(`${ENV[f.base]}${f.path}`);
      expect(`${url.origin}${url.pathname}`, `${leg.domain} (base from ${f.baseSource})`).toBe(
        `${expected.origin}${expected.pathname}`,
      );
      expect(url.searchParams.get('period'), leg.domain).toBe('week');
    }
  });

  it('the fetcher constants are real paths (non-empty, rooted) — a vacuous constant cannot pass the pin', () => {
    for (const [d, f] of Object.entries(FETCHERS)) {
      expect(f.path, d).toMatch(/^\/[a-z]/);
      expect(f.path, d).not.toContain('?');
    }
  });
});
