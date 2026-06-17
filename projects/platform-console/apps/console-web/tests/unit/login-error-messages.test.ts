import { describe, it, expect } from 'vitest';

/**
 * Unit tests for login page error-message logic (Gap C / F5 — TASK-PC-FE-115).
 *
 * The ERROR_MESSAGES map and GENERIC_ERROR constant are kept in
 * app/(auth)/login/page.tsx (server component). We replicate the lookup
 * logic here so it can run in the vitest jsdom environment without
 * importing the Next.js page (which pulls in `next/navigation` redirect
 * not available outside the framework runtime).
 *
 * If the map or constant changes in the source, update this file accordingly.
 */

const ERROR_MESSAGES: Record<string, string> = {
  provider_error: 'IAM 로그인 중 오류가 발생했습니다. 다시 시도해주세요.',
  invalid_state: '로그인 세션이 만료되었습니다. 다시 로그인해주세요.',
  state_mismatch: '보안 검증에 실패했습니다. 다시 로그인해주세요.',
  token_exchange_failed:
    '인증 서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.',
  not_provisioned:
    '운영자 권한이 없는 계정입니다. 관리자에게 권한 부여를 요청하세요.',
  operator_exchange_unavailable:
    '인증 서버 일시 오류가 발생했습니다. 잠시 후 다시 시도해주세요.',
};

const GENERIC_ERROR = '로그인 중 오류가 발생했습니다. 다시 시도해주세요.';

/** Mirrors the resolution logic in login/page.tsx */
function resolveErrorMessage(code: string | undefined): string | null {
  if (!code) return null;
  return ERROR_MESSAGES[code] ?? GENERIC_ERROR;
}

describe('login ERROR_MESSAGES map (Gap C / F5)', () => {
  it('returns null when no error code is present', () => {
    expect(resolveErrorMessage(undefined)).toBeNull();
    expect(resolveErrorMessage('')).toBeNull();
  });

  it('maps provider_error to a visible message', () => {
    expect(resolveErrorMessage('provider_error')).toBeTruthy();
  });

  it('maps invalid_state to a visible message', () => {
    expect(resolveErrorMessage('invalid_state')).toBeTruthy();
  });

  it('maps state_mismatch to a visible message', () => {
    expect(resolveErrorMessage('state_mismatch')).toBeTruthy();
  });

  it('maps token_exchange_failed to a visible message', () => {
    expect(resolveErrorMessage('token_exchange_failed')).toBeTruthy();
  });

  it('maps not_provisioned to a permission-oriented message (distinct from transient error)', () => {
    const msg = resolveErrorMessage('not_provisioned');
    expect(msg).toBeTruthy();
    // Must communicate a permission/provisioning problem, not a retry hint.
    expect(msg).toContain('권한');
  });

  it('maps operator_exchange_unavailable to a retry-oriented message (distinct from permission error)', () => {
    const msg = resolveErrorMessage('operator_exchange_unavailable');
    expect(msg).toBeTruthy();
    // Must communicate a transient/retry situation.
    expect(msg).toContain('다시 시도');
  });

  it('not_provisioned and operator_exchange_unavailable have DISTINCT messages', () => {
    expect(resolveErrorMessage('not_provisioned')).not.toBe(
      resolveErrorMessage('operator_exchange_unavailable'),
    );
  });

  it('returns the generic fallback for an unknown error code (never silent)', () => {
    expect(resolveErrorMessage('some_future_code')).toBe(GENERIC_ERROR);
    expect(resolveErrorMessage('totally_unexpected')).toBe(GENERIC_ERROR);
  });

  it('generic fallback is non-empty', () => {
    expect(GENERIC_ERROR.trim().length).toBeGreaterThan(0);
  });
});
