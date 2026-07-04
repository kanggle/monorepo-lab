import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Same-origin onboarding proxy (`app/api/onboarding/organizations/route.ts`).
 *
 * The pre-operator "create organization" orchestration: read the IAM access
 * token server-side → call the onboarding client → on success re-exchange the
 * operator token + set the session cookies (walk into the console) — with a
 * fail-soft `ready:false` when the durable org is created but the immediate
 * re-exchange fails.
 *
 * `next/headers` cookies() is mocked (jar), and the two downstream server
 * modules (onboarding-client + operator-token-exchange) are mocked so the
 * orchestration is tested in isolation. The real session cookie constants +
 * zod input schema + error classes are used.
 */

const cookieJar = new Map<string, { value: string; opts: Record<string, unknown> }>();
const cookieDeletes: string[] = [];
const cookiesMock = {
  get: (name: string) => {
    const e = cookieJar.get(name);
    return e ? { value: e.value } : undefined;
  },
  set: (name: string, value: string, opts: Record<string, unknown>) => {
    cookieJar.set(name, { value, opts });
  },
  delete: (name: string) => {
    cookieJar.delete(name);
    cookieDeletes.push(name);
  },
};
vi.mock('next/headers', () => ({ cookies: async () => cookiesMock }));

vi.mock('@/shared/lib/logger', () => ({
  logger: { debug: vi.fn(), info: vi.fn(), warn: vi.fn(), error: vi.fn() },
  newRequestId: () => 'req-test',
}));

const createOrganization = vi.fn();
vi.mock('@/features/onboarding/api/onboarding-client', () => ({
  createOrganization: (...args: unknown[]) => createOrganization(...args),
}));

const exchangeForOperatorToken = vi.fn();
vi.mock('@/shared/lib/operator-token-exchange', () => ({
  exchangeForOperatorToken: (...args: unknown[]) =>
    exchangeForOperatorToken(...args),
}));

import { POST } from '@/app/api/onboarding/organizations/route';
import {
  ACCESS_COOKIE,
  OPERATOR_COOKIE,
  TENANT_COOKIE,
} from '@/shared/lib/session';
import { ApiError, OnboardingUnavailableError } from '@/shared/api/errors';

function post(body: unknown) {
  return new Request('http://console.local/api/onboarding/organizations', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}

const VALID = { tenantId: 'acme-corp', organizationName: 'Acme Corporation' };

beforeEach(() => {
  cookieJar.clear();
  cookieDeletes.length = 0;
  createOrganization.mockReset();
  exchangeForOperatorToken.mockReset();
});

describe('POST /api/onboarding/organizations — guards', () => {
  it('401 when there is no IAM access cookie (not even a pre-operator)', async () => {
    const res = await POST(post(VALID));
    expect(res.status).toBe(401);
    expect((await res.json()).code).toBe('TOKEN_INVALID');
    expect(createOrganization).not.toHaveBeenCalled();
  });

  it('400 VALIDATION_ERROR on a bad tenant slug (never reaches the backend)', async () => {
    cookieJar.set(ACCESS_COOKIE, { value: 'iam.acc', opts: {} });
    const res = await POST(post({ ...VALID, tenantId: 'Bad_Slug!' }));
    expect(res.status).toBe(400);
    expect((await res.json()).code).toBe('VALIDATION_ERROR');
    expect(createOrganization).not.toHaveBeenCalled();
  });
});

describe('POST /api/onboarding/organizations — success + console entry', () => {
  it('201 ready:true — sets operator + active-tenant cookies from the re-exchange', async () => {
    cookieJar.set(ACCESS_COOKIE, { value: 'iam.acc', opts: {} });
    createOrganization.mockResolvedValue({
      tenantId: 'acme-corp',
      operatorId: 'op-1',
      roles: ['TENANT_ADMIN', 'TENANT_BILLING_ADMIN'],
      status: 'ACTIVE',
    });
    exchangeForOperatorToken.mockResolvedValue({
      accessToken: 'op.jwt',
      expiresIn: 900,
    });

    const res = await POST(post(VALID));
    expect(res.status).toBe(201);
    expect(await res.json()).toEqual({ tenantId: 'acme-corp', ready: true });

    // The onboarding client got the access token as the subjectToken.
    expect(createOrganization).toHaveBeenCalledWith(VALID, 'iam.acc');
    expect(exchangeForOperatorToken).toHaveBeenCalledWith('iam.acc');
    // Operator + active-tenant cookies set → the owner enters the console.
    expect(cookieJar.get(OPERATOR_COOKIE)?.value).toBe('op.jwt');
    expect(cookieJar.get(OPERATOR_COOKIE)?.opts).toMatchObject({ maxAge: 900 });
    expect(cookieJar.get(TENANT_COOKIE)?.value).toBe('acme-corp');
  });

  it('201 ready:false — the org is created but the immediate re-exchange failed (no cookie, no rollback)', async () => {
    cookieJar.set(ACCESS_COOKIE, { value: 'iam.acc', opts: {} });
    createOrganization.mockResolvedValue({
      tenantId: 'acme-corp',
      operatorId: 'op-1',
      roles: ['TENANT_ADMIN', 'TENANT_BILLING_ADMIN'],
      status: 'ACTIVE',
    });
    exchangeForOperatorToken.mockRejectedValue(
      new OnboardingUnavailableError('timeout', 'TIMEOUT', 'x'),
    );

    const res = await POST(post(VALID));
    expect(res.status).toBe(201);
    expect(await res.json()).toEqual({ tenantId: 'acme-corp', ready: false });
    // No operator cookie minted; the durable org is NOT undone.
    expect(cookieJar.has(OPERATOR_COOKIE)).toBe(false);
  });
});

describe('POST /api/onboarding/organizations — producer error passthrough', () => {
  it('409 TENANT_ALREADY_EXISTS passes through as 409 (no re-exchange)', async () => {
    cookieJar.set(ACCESS_COOKIE, { value: 'iam.acc', opts: {} });
    createOrganization.mockRejectedValue(
      new ApiError(409, 'TENANT_ALREADY_EXISTS', 'slug taken'),
    );
    const res = await POST(post(VALID));
    expect(res.status).toBe(409);
    expect((await res.json()).code).toBe('TENANT_ALREADY_EXISTS');
    expect(exchangeForOperatorToken).not.toHaveBeenCalled();
  });

  it('401 subject token rejected → 401 (re-login)', async () => {
    cookieJar.set(ACCESS_COOKIE, { value: 'iam.acc', opts: {} });
    createOrganization.mockRejectedValue(
      new ApiError(401, 'TOKEN_INVALID', 'rejected'),
    );
    const res = await POST(post(VALID));
    expect(res.status).toBe(401);
    expect((await res.json()).code).toBe('TOKEN_INVALID');
  });

  it('onboarding unavailable → 503 degrade', async () => {
    cookieJar.set(ACCESS_COOKIE, { value: 'iam.acc', opts: {} });
    createOrganization.mockRejectedValue(
      new OnboardingUnavailableError('downstream', 'DOWNSTREAM_ERROR', 'x'),
    );
    const res = await POST(post(VALID));
    expect(res.status).toBe(503);
    expect(exchangeForOperatorToken).not.toHaveBeenCalled();
  });
});
