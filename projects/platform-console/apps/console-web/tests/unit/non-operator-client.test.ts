import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { apiFetch } from '@/shared/api/client';
import { ApiError } from '@/shared/api/errors';

/**
 * TASK-MONO-773 S6 — console-integration-contract § 2.6.1 / § 2.6.4: the browser API client after a `401`.
 * When `POST /api/auth/refresh` answers `403 NOT_AN_OPERATOR` (the session is a valid identity session that is
 * not an operator — the BFF kept the identity cookies and set the non-operator marker), the client navigates to
 * the tenant-creation screen `/new-tenant`: never `/login` («session expired» is false) and never a retry of the
 * operator call.
 *
 * 🔵 The retired `503 OPERATOR_CHECK_UNAVAILABLE` (773 S5 removed its only IAM source) is now an ordinary
 * refresh failure — reasonless `/login?redirect=…`.
 */

vi.mock('next/navigation', () => ({
  useRouter: () => ({ refresh: vi.fn(), push: vi.fn(), replace: vi.fn() }),
}));

const assignMock = vi.fn();
let originalLocation: Location;

function stubLocation(pathname: string, search = '') {
  Object.defineProperty(window, 'location', {
    configurable: true,
    writable: true,
    value: { assign: assignMock, pathname, search },
  });
}

function json(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function refreshAnswers(reply: () => Response) {
  const fetchMock = vi.fn((url: string) =>
    Promise.resolve(String(url) === '/api/auth/refresh' ? reply() : json({ code: 'TOKEN_INVALID' }, 401)),
  );
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

beforeEach(async () => {
  originalLocation = window.location;
  assignMock.mockReset();
  vi.unstubAllGlobals();
  // client.ts shares one in-flight refresh and releases it on a `setTimeout(0)`.
  await new Promise((resolve) => setTimeout(resolve, 0));
});
afterEach(() => {
  Object.defineProperty(window, 'location', { configurable: true, writable: true, value: originalLocation });
});

describe('apiFetch — refresh answered 403 NOT_AN_OPERATOR (§ 2.6.4)', () => {
  it('🔴 navigates to /new-tenant and throws NOT_AN_OPERATOR — no retry of the original call', async () => {
    stubLocation('/accounts', '?q=a');
    const fetchMock = refreshAnswers(() => json({ code: 'NOT_AN_OPERATOR', message: 'not an operator' }, 403));

    const err = await apiFetch('/api/accounts').catch((e: unknown) => e);

    expect(err).toBeInstanceOf(ApiError);
    expect((err as ApiError).code).toBe('NOT_AN_OPERATOR');
    expect(assignMock).toHaveBeenCalledWith('/new-tenant');
    // original call + refresh only — the operator call is not retried
    expect(fetchMock.mock.calls.filter(([u]) => String(u) === '/api/accounts')).toHaveLength(1);
  });

  it('🔵 control — 403 MFA_REQUIRED still steps up (unchanged)', async () => {
    stubLocation('/accounts');
    refreshAnswers(() => json({ code: 'MFA_REQUIRED' }, 403));
    await apiFetch('/api/accounts').catch(() => undefined);
    expect(assignMock).toHaveBeenCalledWith(
      `/api/auth/step-up?redirect=${encodeURIComponent('/accounts')}`,
    );
  });

  it('🔴 the retired 503 OPERATOR_CHECK_UNAVAILABLE is an ordinary failure — reasonless /login?redirect=…', async () => {
    stubLocation('/accounts');
    refreshAnswers(() => json({ code: 'OPERATOR_CHECK_UNAVAILABLE' }, 503));
    await apiFetch('/api/accounts').catch(() => undefined);
    expect(assignMock).toHaveBeenCalledWith(`/login?redirect=${encodeURIComponent('/accounts')}`);
  });
});
