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

// TASK-MONO-773 S6 (§ 2.6.4 «Self-mode success») — the new tenant is assumed through the SAME default-assume
// function the callback / refresh use (it reads the registry + runs the assume exchange — mocked here).
const establishDefaultTenant = vi.fn();
vi.mock('@/shared/lib/active-tenant-default', () => ({
  establishDefaultTenant: (...args: unknown[]) => establishDefaultTenant(...args),
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
  establishDefaultTenant.mockReset();
  establishDefaultTenant.mockResolvedValue('acme-corp');
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
  it('201 ready:true — sets the operator cookie, drops the non-operator marker, assumes the NEW tenant via the shared default-assume (TASK-MONO-773 S6)', async () => {
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
    // Operator cookie set → the owner enters the console.
    expect(cookieJar.get(OPERATOR_COOKIE)?.value).toBe('op.jwt');
    expect(cookieJar.get(OPERATOR_COOKIE)?.opts).toMatchObject({ maxAge: 900 });
    // TASK-MONO-773 S6 — the session is no longer a non-operator one: the marker goes in the same response.
    expect(cookieDeletes).toContain('console_non_operator');
    // TASK-MONO-773 S6 — the new tenant is ASSUMED (tenant cookie + assumed token together, § 2.7) by the
    // same function callback/refresh use, with the new tenant as the preferred choice — never a bare tenant
    // cookie without its assumed token.
    expect(establishDefaultTenant).toHaveBeenCalledTimes(1);
    expect(establishDefaultTenant.mock.calls[0][1]).toMatchObject({
      accessToken: 'iam.acc',
      operatorToken: 'op.jwt',
      preferredTenant: 'acme-corp',
    });
    expect(cookieJar.has(TENANT_COOKIE), 'no bare tenant cookie from the route itself').toBe(false);
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
    // TASK-MONO-773 S6 — the marker stays: the session is still a non-operator one until an exchange succeeds.
    expect(cookieDeletes).not.toContain('console_non_operator');
    expect(establishDefaultTenant).not.toHaveBeenCalled();
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

  // TASK-MONO-784 S6 — the owned-tenant limit must pass through as its own
  // 422, never folded into the 503 degrade branch below (it is a business
  // refusal, not an outage — retrying never helps).
  it('422 OPERATOR_OWNED_TENANT_LIMIT_EXCEEDED passes through as 422 (no re-exchange)', async () => {
    cookieJar.set(ACCESS_COOKIE, { value: 'iam.acc', opts: {} });
    createOrganization.mockRejectedValue(
      new ApiError(422, 'OPERATOR_OWNED_TENANT_LIMIT_EXCEEDED', 'limit exceeded'),
    );
    const res = await POST(post(VALID));
    expect(res.status).toBe(422);
    expect((await res.json()).code).toBe('OPERATOR_OWNED_TENANT_LIMIT_EXCEEDED');
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
