import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * TASK-MONO-771 S5 — the entry-policy server API over the real
 * `callAdminGateway` core (`fetch` + env + session mocked, the
 * `tenants-client.test.ts` pattern): the § 2.4.3.3 header matrix
 * (PUT = `X-Operator-Reason` percent-encoded, NEVER `Idempotency-Key`;
 * GET = no mutation header) and the error taxonomy (403 → ApiError,
 * 503 → TenantsUnavailableError — only this control degrades).
 */

const { ENV } = vi.hoisted(() => ({
  ENV: {
    IAM_ADMIN_API_BASE: 'http://iam.local',
    TENANTS_TIMEOUT_MS: 50,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

const session = vi.hoisted(() => ({
  operatorToken: 'op.jwt' as string | null,
  activeTenant: 'acme' as string | null,
}));
vi.mock('@/shared/lib/session', () => ({
  getOperatorToken: async () => session.operatorToken,
  getActiveTenant: async () => session.activeTenant,
}));

vi.mock('@/shared/lib/logger', () => ({
  logger: { debug: vi.fn(), info: vi.fn(), warn: vi.fn(), error: vi.fn() },
  newRequestId: () => 'req-test',
}));

import {
  getEntryPolicy,
  setEntryPolicy,
  getEnrolmentSummary,
} from '@/features/tenant-entry-policy/api/entry-policy-api';
import { TenantsUnavailableError } from '@/shared/api/errors';

const POLICY = { tenantId: 'acme', requireMfa: true, updatedAt: '2026-10-09T00:00:00Z', updatedBy: 'op' };

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

beforeEach(() => {
  session.operatorToken = 'op.jwt';
  session.activeTenant = 'acme';
  vi.unstubAllGlobals();
});

describe('entry-policy api — header matrix (§ 2.4.3.3)', () => {
  it('🔴 PUT: operator token + X-Tenant-Id + percent-encoded reason, body {requireMfa}, NO Idempotency-Key', async () => {
    const fetchMock = vi.fn().mockResolvedValue(json(POLICY));
    vi.stubGlobal('fetch', fetchMock);

    expect(await setEntryPolicy('acme', true, '회사 보안 정책')).toEqual(POLICY);

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('http://iam.local/api/admin/tenants/acme/entry-policy');
    expect((init as RequestInit).method).toBe('PUT');
    const headers = (init as RequestInit).headers as Record<string, string>;
    expect(headers.Authorization).toBe('Bearer op.jwt');
    expect(headers['X-Tenant-Id']).toBe('acme');
    expect(headers['X-Operator-Reason']).toBe(encodeURIComponent('회사 보안 정책'));
    expect(headers['Idempotency-Key']).toBeUndefined();
    expect(JSON.parse((init as RequestInit).body as string)).toEqual({ requireMfa: true });
  });

  it('GET policy / enrolment-summary carry no mutation header', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(json(POLICY))
      .mockResolvedValueOnce(json({ tenantId: 'acme', operators: 1, enrolled: 0, notEnrolled: 1, unlinked: 0 }));
    vi.stubGlobal('fetch', fetchMock);

    await getEntryPolicy('acme');
    await getEnrolmentSummary('acme');

    expect(fetchMock.mock.calls[1][0]).toBe(
      'http://iam.local/api/admin/tenants/acme/entry-policy/enrolment-summary',
    );
    for (const [, init] of fetchMock.mock.calls) {
      const headers = (init as RequestInit).headers as Record<string, string>;
      expect(headers['X-Operator-Reason']).toBeUndefined();
      expect(headers['Idempotency-Key']).toBeUndefined();
    }
  });

  it('a blank reason never reaches the producer (400 REASON_REQUIRED, no fetch)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    await expect(setEntryPolicy('acme', true, '   ')).rejects.toMatchObject({
      status: 400,
      code: 'REASON_REQUIRED',
    });
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe('entry-policy api — error taxonomy', () => {
  it('403 TENANT_SCOPE_DENIED → ApiError passthrough (inline «권한 없음»)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json({ code: 'TENANT_SCOPE_DENIED' }, 403)));
    await expect(getEntryPolicy('other')).rejects.toMatchObject({
      name: 'ApiError',
      status: 403,
      code: 'TENANT_SCOPE_DENIED',
    });
  });

  it('503 → TenantsUnavailableError (only this control degrades)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json({ code: 'DOWNSTREAM_ERROR' }, 503)));
    await expect(getEnrolmentSummary('acme')).rejects.toBeInstanceOf(TenantsUnavailableError);
  });

  it('no operator token → 401, no fetch', async () => {
    session.operatorToken = null;
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    await expect(getEntryPolicy('acme')).rejects.toMatchObject({ status: 401 });
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
