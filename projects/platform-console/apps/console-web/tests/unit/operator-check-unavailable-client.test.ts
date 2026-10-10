import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { apiFetch } from '@/shared/api/client';
import { ApiError } from '@/shared/api/errors';

/**
 * TASK-MONO-772 S4 (§ 2.6.3) — the browser API client after a `401`: when `POST /api/auth/refresh` answers
 * `503 OPERATOR_CHECK_UNAVAILABLE` (IAM could not ask whether the personal account is an operator — the BFF
 * kept every cookie), the client goes to `/login` WITH the reason and the current page, never to a reasonless
 * `/login?redirect=…`. A `503` with another code stays a plain failure.
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
  vi.stubGlobal(
    'fetch',
    vi.fn((url: string) =>
      Promise.resolve(String(url) === '/api/auth/refresh' ? reply() : json({ code: 'TOKEN_INVALID' }, 401)),
    ),
  );
}

beforeEach(async () => {
  originalLocation = window.location;
  assignMock.mockReset();
  vi.unstubAllGlobals();
  // client.ts shares one in-flight refresh and releases it on a `setTimeout(0)`; let the previous cell's
  // promise go, or this cell reads the previous cell's refresh answer.
  await new Promise((resolve) => setTimeout(resolve, 0));
});
afterEach(() => {
  Object.defineProperty(window, 'location', { configurable: true, writable: true, value: originalLocation });
});

describe('apiFetch — refresh answered 503 OPERATOR_CHECK_UNAVAILABLE (§ 2.6.3)', () => {
  it('🔴 /login?error=operator_check_unavailable&redirect=<current> 로 가고 OPERATOR_CHECK_UNAVAILABLE 을 던진다', async () => {
    stubLocation('/iam/accounts', '?q=a');
    refreshAnswers(() => json({ code: 'OPERATOR_CHECK_UNAVAILABLE', message: 'operator check unavailable' }, 503));

    const err = await apiFetch('/api/accounts').catch((e: unknown) => e);

    expect(err).toBeInstanceOf(ApiError);
    expect((err as ApiError).code).toBe('OPERATOR_CHECK_UNAVAILABLE');
    expect(assignMock).toHaveBeenCalledWith(
      `/login?error=operator_check_unavailable&redirect=${encodeURIComponent('/iam/accounts?q=a')}`,
    );
  });

  it('🔵 대조군 — 다른 코드의 503 은 지금처럼 사유 없는 /login?redirect=…', async () => {
    stubLocation('/iam/accounts');
    refreshAnswers(() => json({ code: 'DOWNSTREAM_ERROR' }, 503));

    await apiFetch('/api/accounts').catch(() => undefined);

    expect(assignMock).toHaveBeenCalledWith(`/login?redirect=${encodeURIComponent('/iam/accounts')}`);
  });
});
