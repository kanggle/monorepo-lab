import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * TASK-PC-FE-373 — the console must read the alert row the PRODUCER really sends.
 *
 * Producer: wms `AlertLogResponse` = { id, alertType, warehouseId, locationId, skuId, lotId,
 * thresholdQty, actualQty, detectedAt, acknowledgedAt, acknowledgedBy, projectedAt }.
 * NO `alertId`, NO `message`, NO `acknowledged`. (admin-service-api.md § 1.6.)
 *
 * Control: the sample fixture used to carry `alertId`/`message`/`acknowledged`, so every
 * test fed from it stayed green while the real producer shape degraded the whole /wms overview.
 * These rows are written by hand from the producer DTO on purpose - never from the fixture.
 */

const cookieJar = new Map<string, string>();
vi.mock('next/headers', () => ({
  cookies: async () => ({
    get: (n: string) =>
      cookieJar.has(n) ? { value: cookieJar.get(n)! } : undefined,
  }),
}));
vi.mock('next/navigation', () => ({
  redirect: (to: string) => {
    throw new Error(`REDIRECT:${to}`);
  },
}));

const { ENV } = vi.hoisted(() => ({
  ENV: {
    OIDC_ISSUER_URL: 'http://iam.local',
    OIDC_CLIENT_ID: 'platform-console-web',
    OIDC_REDIRECT_URI: 'http://console.local/api/auth/callback',
    OIDC_SCOPE: 'openid profile email tenant.read',
    CONSOLE_REGISTRY_URL: 'http://iam.local/api/admin/console/registry',
    REGISTRY_TIMEOUT_MS: 50,
    CONSOLE_TOKEN_EXCHANGE_URL: 'http://iam.local/api/admin/auth/token-exchange',
    TOKEN_EXCHANGE_TIMEOUT_MS: 50,
    IAM_ADMIN_API_BASE: 'http://iam.local',
    ACCOUNTS_TIMEOUT_MS: 50,
    AUDIT_TIMEOUT_MS: 50,
    OPERATORS_TIMEOUT_MS: 50,
    WMS_ADMIN_BASE_URL: 'http://wms.local/api/v1/admin',
    WMS_TIMEOUT_MS: 50,
    LOG_LEVEL: 'info' as const,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

import { listAlerts } from '@/features/wms-ops/api/wms-alerts-api';
import { getWmsSectionState } from '@/features/wms-ops/api/wms-state';
import { AlertPageSchema } from '@/features/wms-ops/api/types';
import { ACCESS_COOKIE } from '@/shared/lib/session';

/** The row the 35th window observed: warehouseId null, locationId set, never acknowledged. */
const PRODUCER_ROW = {
  id: '3f2b1c9e-0000-4000-8000-000000000001',
  alertType: 'LOW_STOCK',
  warehouseId: null,
  locationId: '3f2b1c9e-0000-4000-8000-0000000000a1',
  skuId: '3f2b1c9e-0000-4000-8000-0000000000b1',
  lotId: null,
  thresholdQty: 10,
  actualQty: 3,
  detectedAt: '2026-10-25T01:00:00Z',
  acknowledgedAt: null,
  acknowledgedBy: null,
  projectedAt: '2026-10-25T01:00:01Z',
};
const PAGE_META = { number: 0, size: 20, totalElements: 1, totalPages: 1 };

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

beforeEach(() => {
  cookieJar.clear();
  cookieJar.set(ACCESS_COOKIE, 'GAP-ACCESS');
  vi.unstubAllGlobals();
});

describe('TASK-PC-FE-373 AC-2 - producer-shaped row parses (no alertId / message / acknowledged)', () => {
  it('AlertPageSchema keeps the row, keyed by id', () => {
    const page = AlertPageSchema.parse({ content: [PRODUCER_ROW], page: PAGE_META });
    expect(page.content).toHaveLength(1);
    expect(page.content[0].id).toBe(PRODUCER_ROW.id);
    expect(page.content[0].warehouseId).toBeNull();
    expect(page.content[0].locationId).toBe(PRODUCER_ROW.locationId);
  });

  it('listAlerts() returns the row instead of throwing', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse({ content: [PRODUCER_ROW], page: PAGE_META })),
    );
    const r = await listAlerts();
    expect(r.data.content.map((a) => a.id)).toEqual([PRODUCER_ROW.id]);
  });

  it('getWmsSectionState: ONE alert row does not degrade the overview, and the row is seeded', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse({ content: [PRODUCER_ROW], page: PAGE_META })),
    );
    const state = await getWmsSectionState(true);
    expect(state.degraded).toBe(false);
    expect(state.alerts?.content.map((a) => a.id)).toEqual([PRODUCER_ROW.id]);
  });

  it('control: zero alerts still renders (the path that always worked)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({
          content: [],
          page: { number: 0, size: 20, totalElements: 0, totalPages: 0 },
        }),
      ),
    );
    const state = await getWmsSectionState(true);
    expect(state.degraded).toBe(false);
    expect(state.alerts?.content).toEqual([]);
  });
});

describe('TASK-PC-FE-373 AC-5 - one unparseable row does not erase the overview', () => {
  const BAD_ROW = { ...PRODUCER_ROW, id: undefined }; // JSON drops it -> `id` absent

  it('getWmsSectionState: good row + id-less row -> not degraded, good row kept, exclusion counted', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({
          content: [PRODUCER_ROW, BAD_ROW],
          page: { ...PAGE_META, totalElements: 2 },
        }),
      ),
    );
    const state = await getWmsSectionState(true);
    expect(state.degraded).toBe(false);
    expect(state.alerts?.content.map((a) => a.id)).toEqual([PRODUCER_ROW.id]);
    expect(state.alerts?.excludedRows).toBe(1);
  });

  it('a non-object row is excluded too (not a throw)', () => {
    const page = AlertPageSchema.parse({
      content: [PRODUCER_ROW, null, 'x'],
      page: PAGE_META,
    });
    expect(page.content).toHaveLength(1);
    expect(page.excludedRows).toBe(2);
  });

  it('control: a broken page ENVELOPE (no `page`) still throws - only rows are tolerant', () => {
    expect(() => AlertPageSchema.parse({ content: [PRODUCER_ROW] })).toThrow();
  });
});
