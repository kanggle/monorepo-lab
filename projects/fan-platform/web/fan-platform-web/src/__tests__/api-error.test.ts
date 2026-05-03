import { describe, it, expect } from 'vitest';
import { ApiError, toApiError } from '@/shared/api/errors';

describe('ApiError', () => {
  it('flags 401 / UNAUTHORIZED as an auth error', () => {
    expect(new ApiError(401, { code: 'UNAUTHORIZED', message: 'no token' }).isAuthError).toBe(
      true,
    );
    expect(new ApiError(403, { code: 'UNAUTHORIZED', message: 'x' }).isAuthError).toBe(true);
  });

  it('flags TENANT_FORBIDDEN distinctly from generic FORBIDDEN', () => {
    const tenant = new ApiError(403, { code: 'TENANT_FORBIDDEN', message: 'x' });
    const forbidden = new ApiError(403, { code: 'FORBIDDEN', message: 'x' });
    expect(tenant.isTenantForbidden).toBe(true);
    expect(forbidden.isTenantForbidden).toBe(false);
  });

  it('flags MEMBERSHIP_REQUIRED for visibility-tier blocks', () => {
    const m = new ApiError(403, {
      code: 'MEMBERSHIP_REQUIRED',
      message: 'requires PREMIUM',
      details: { requiredTier: 'PREMIUM' },
    });
    expect(m.isMembershipRequired).toBe(true);
  });

  it('toApiError handles non-JSON responses gracefully', async () => {
    const res = new Response('plain text', { status: 502, statusText: 'Bad Gateway' });
    const err = await toApiError(res);
    expect(err.status).toBe(502);
    expect(err.code).toBe('UNKNOWN');
    expect(err.message).toBe('Bad Gateway');
  });

  it('toApiError parses the canonical error envelope', async () => {
    const res = new Response(
      JSON.stringify({ code: 'POST_NOT_FOUND', message: 'gone' }),
      { status: 404, headers: { 'Content-Type': 'application/json' } },
    );
    const err = await toApiError(res);
    expect(err.status).toBe(404);
    expect(err.code).toBe('POST_NOT_FOUND');
  });
});
