import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * Server-only self-service onboarding client
 * (`features/onboarding/api/onboarding-client.ts`).
 *
 * Authoritative producer contract (verbatim):
 *   iam/specs/contracts/http/onboarding-api.md
 *   § POST /api/admin/onboarding/organizations (+ ADR-MONO-044).
 *
 * `fetch` is mocked; getServerEnv() + logger are mocked (same lane as the
 * operator-token-exchange test). Asserts the § 2.1 invariant that the
 * subjectToken is never logged.
 */

const { ENV } = vi.hoisted(() => ({
  ENV: {
    CONSOLE_ONBOARDING_URL:
      'http://iam.local/api/admin/onboarding/organizations',
    ONBOARDING_TIMEOUT_MS: 50,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

const logged: string[] = [];
vi.mock('@/shared/lib/logger', () => ({
  logger: {
    debug: (m: string, f?: unknown) => logged.push(m + JSON.stringify(f ?? {})),
    info: (m: string, f?: unknown) => logged.push(m + JSON.stringify(f ?? {})),
    warn: (m: string, f?: unknown) => logged.push(m + JSON.stringify(f ?? {})),
    error: (m: string, f?: unknown) => logged.push(m + JSON.stringify(f ?? {})),
  },
  newRequestId: () => 'req-test',
}));

import { createOrganization } from '@/features/onboarding/api/onboarding-client';
import { ApiError, OnboardingUnavailableError } from '@/shared/api/errors';

const SUBJECT = 'iam.oidc.access.token.value.SECRET';
const INPUT = { tenantId: 'acme-corp', organizationName: 'Acme Corporation' };

function ok201() {
  return new Response(
    JSON.stringify({
      tenantId: 'acme-corp',
      operatorId: 'op-uuid-v7',
      roles: ['TENANT_ADMIN', 'TENANT_BILLING_ADMIN'],
      status: 'ACTIVE',
    }),
    { status: 201, headers: { 'Content-Type': 'application/json' } },
  );
}

beforeEach(() => {
  logged.length = 0;
  vi.unstubAllGlobals();
});

describe('createOrganization — success', () => {
  it('returns the parsed 201 payload', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ok201()));
    const out = await createOrganization(INPUT, SUBJECT);
    expect(out).toEqual({
      tenantId: 'acme-corp',
      operatorId: 'op-uuid-v7',
      roles: ['TENANT_ADMIN', 'TENANT_BILLING_ADMIN'],
      status: 'ACTIVE',
    });
  });

  it('POSTs exactly {subjectToken, tenantId, organizationName} to the producer URL', async () => {
    const fetchMock = vi.fn().mockResolvedValue(ok201());
    vi.stubGlobal('fetch', fetchMock);
    await createOrganization(INPUT, SUBJECT);

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('http://iam.local/api/admin/onboarding/organizations');
    expect((init as RequestInit).method).toBe('POST');
    const body = JSON.parse((init as RequestInit).body as string);
    expect(Object.keys(body).sort()).toEqual([
      'organizationName',
      'subjectToken',
      'tenantId',
    ]);
    expect(body.subjectToken).toBe(SUBJECT);
    expect(body.tenantId).toBe('acme-corp');
    expect(body.organizationName).toBe('Acme Corporation');
  });

  it('never logs the subjectToken', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(ok201()));
    await createOrganization(INPUT, SUBJECT);
    expect(logged.join('\n')).not.toContain(SUBJECT);
  });
});

describe('createOrganization — inline-actionable producer errors', () => {
  it('401 → ApiError(401, TOKEN_INVALID)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ code: 'TOKEN_INVALID' }), {
          status: 401,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    );
    const err = await createOrganization(INPUT, SUBJECT).catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(401);
    expect(err.code).toBe('TOKEN_INVALID');
    expect(err.message).not.toContain(SUBJECT);
  });

  it('400 VALIDATION_ERROR → ApiError(400)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({ code: 'VALIDATION_ERROR', message: 'bad slug' }),
          { status: 400, headers: { 'Content-Type': 'application/json' } },
        ),
      ),
    );
    await expect(createOrganization(INPUT, SUBJECT)).rejects.toMatchObject({
      name: 'ApiError',
      status: 400,
      code: 'VALIDATION_ERROR',
    });
  });

  it('409 TENANT_ALREADY_EXISTS → ApiError(409, passthrough)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ code: 'TENANT_ALREADY_EXISTS' }), {
          status: 409,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    );
    await expect(createOrganization(INPUT, SUBJECT)).rejects.toMatchObject({
      name: 'ApiError',
      status: 409,
      code: 'TENANT_ALREADY_EXISTS',
    });
  });
});

describe('createOrganization — unavailable', () => {
  it('503 → OnboardingUnavailableError', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ code: 'DOWNSTREAM_ERROR' }), {
          status: 503,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    );
    await expect(createOrganization(INPUT, SUBJECT)).rejects.toMatchObject({
      name: 'OnboardingUnavailableError',
      reason: 'downstream',
    });
  });

  it('unexpected 201 shape → OnboardingUnavailableError(BAD_RESPONSE_SHAPE)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ tenantId: 'acme-corp' }), {
          status: 201,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    );
    await expect(createOrganization(INPUT, SUBJECT)).rejects.toMatchObject({
      name: 'OnboardingUnavailableError',
      code: 'BAD_RESPONSE_SHAPE',
    });
  });

  it('network failure → OnboardingUnavailableError (no subjectToken in message)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockRejectedValue(new Error('ECONNREFUSED iam.local')),
    );
    const err = await createOrganization(INPUT, SUBJECT).catch((e) => e);
    expect(err).toBeInstanceOf(OnboardingUnavailableError);
    expect(err.message).not.toContain(SUBJECT);
  });

  it('AbortController hard timeout → OnboardingUnavailableError(TIMEOUT)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn((_url: string, init?: RequestInit) => {
        return new Promise((_resolve, reject) => {
          init?.signal?.addEventListener('abort', () => {
            const e = new Error('aborted');
            e.name = 'AbortError';
            reject(e);
          });
        });
      }),
    );
    await expect(createOrganization(INPUT, SUBJECT)).rejects.toMatchObject({
      name: 'OnboardingUnavailableError',
      reason: 'timeout',
      code: 'TIMEOUT',
    });
  });
});
