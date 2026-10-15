import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * TASK-MONO-773 S6 — the onboarding BFF pieces behind the «테넌트 생성» screen (console-integration-contract
 * § 2.6.4; IAM `onboarding-api.md` — trust gate · `received-invitations`).
 *
 *   1. `createOrganization` passes IAM's trust-gate refusals through as INLINE answers — never as the
 *      «onboarding unavailable» degrade (that would tell an unverified account «retry later», which never helps):
 *        `403 EMAIL_NOT_VERIFIED` · `403 ONBOARDING_ACCOUNT_NOT_ELIGIBLE` · `409 OPERATOR_ALREADY_PROVISIONED` ·
 *        `429 RATE_LIMITED` (gateway, `Retry-After`).
 *   2. `POST /api/onboarding/received-invitations` (the BFF the contract names) — server-side, the IAM access cookie
 *      as `subjectToken`, to IAM `POST …/onboarding/received-invitations` (derived from the configured onboarding
 *      URL — same host, sibling path). `200` passes through (`accountState` + `items`, never a token or link);
 *      IAM `503` stays `503` («모른다» ≠ «없음»); no access cookie → `401` with no IAM call.
 */

const cookieJar = new Map<string, string>();
vi.mock('next/headers', () => ({
  cookies: async () => ({
    get: (n: string) => (cookieJar.has(n) ? { value: cookieJar.get(n)! } : undefined),
    set: () => undefined,
    delete: () => undefined,
  }),
}));

const { ENV } = vi.hoisted(() => ({
  ENV: {
    CONSOLE_ONBOARDING_URL: 'http://iam.local/api/admin/onboarding/organizations',
    ONBOARDING_TIMEOUT_MS: 200,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));
vi.mock('@/shared/lib/logger', () => ({
  logger: { debug: vi.fn(), info: vi.fn(), warn: vi.fn(), error: vi.fn() },
  newRequestId: () => 'req-test',
}));

import { createOrganization } from '@/features/onboarding/api/onboarding-client';
import { POST as receivedInvitationsPOST } from '@/app/api/onboarding/received-invitations/route';
import { ApiError, OnboardingUnavailableError } from '@/shared/api/errors';
import { ACCESS_COOKIE } from '@/shared/lib/session';

function json(body: unknown, status: number, headers: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  });
}

const INPUT = { tenantId: 'acme-corp', organizationName: 'Acme Corporation' };

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
});

describe('1 — createOrganization: trust-gate refusals are inline answers, not «unavailable»', () => {
  it.each([
    [403, 'EMAIL_NOT_VERIFIED'],
    [403, 'ONBOARDING_ACCOUNT_NOT_ELIGIBLE'],
    [409, 'OPERATOR_ALREADY_PROVISIONED'],
    [429, 'RATE_LIMITED'],
  ])('🔴 %i %s → ApiError(%i, %s)', async (status, code) => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json({ code, message: 'm' }, status, { 'Retry-After': '60' })));
    const err = await createOrganization(INPUT, 'subject').catch((e: unknown) => e);
    expect(err).not.toBeInstanceOf(OnboardingUnavailableError);
    expect(err).toBeInstanceOf(ApiError);
    expect((err as ApiError).status).toBe(status);
    expect((err as ApiError).code).toBe(code);
  });

  it('🔵 control — 503 is still «unavailable»', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json({ code: 'DOWNSTREAM_ERROR' }, 503)));
    const err = await createOrganization(INPUT, 'subject').catch((e: unknown) => e);
    expect(err).toBeInstanceOf(OnboardingUnavailableError);
  });
});

function postReceived() {
  return receivedInvitationsPOST(
    new Request('http://console.local/api/onboarding/received-invitations', { method: 'POST' }),
  );
}

describe('2 — POST /api/onboarding/received-invitations', () => {
  const ITEMS = [
    {
      invitationId: 'inv-1',
      tenantId: 'acme-corp',
      tenantDisplayName: 'Acme Corp',
      roles: ['SUPPORT_LOCK'],
      firstAdmin: false,
      expiresAt: '2026-10-22T10:00:00Z',
      invitedAt: '2026-10-15T10:00:00Z',
    },
  ];

  it('🔴 200 — subjectToken = the IAM access cookie, sent to the sibling IAM path; answer passes through', async () => {
    cookieJar.set(ACCESS_COOKIE, 'iam.acc');
    const fetchMock = vi.fn().mockResolvedValue(json({ accountState: 'VERIFIED', items: ITEMS }, 200));
    vi.stubGlobal('fetch', fetchMock);

    const res = await postReceived();

    expect(res.status).toBe(200);
    expect(await res.json()).toEqual({ accountState: 'VERIFIED', items: ITEMS });
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(String(url)).toBe('http://iam.local/api/admin/onboarding/received-invitations');
    expect(init.method).toBe('POST');
    expect(JSON.parse(String(init.body))).toEqual({ subjectToken: 'iam.acc' });
    // never an operator credential on this call
    expect(new Headers(init.headers).get('Authorization')).toBeNull();
  });

  it('🔴 EMAIL_NOT_VERIFIED with no items passes through as 200', async () => {
    cookieJar.set(ACCESS_COOKIE, 'iam.acc');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json({ accountState: 'EMAIL_NOT_VERIFIED', items: [] }, 200)));
    const res = await postReceived();
    expect(res.status).toBe(200);
    expect(await res.json()).toEqual({ accountState: 'EMAIL_NOT_VERIFIED', items: [] });
  });

  it('🔴 IAM 503 → 503 (never an empty list — «모른다» ≠ «없음»)', async () => {
    cookieJar.set(ACCESS_COOKIE, 'iam.acc');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json({ code: 'DOWNSTREAM_ERROR' }, 503)));
    const res = await postReceived();
    expect(res.status).toBe(503);
    expect(((await res.json()) as { items?: unknown }).items).toBeUndefined();
  });

  it('🔴 no access cookie → 401, no IAM call', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await postReceived();
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
