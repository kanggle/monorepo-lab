import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { LoginForm } from '@/features/auth/ui/LoginForm';

/**
 * TASK-FE-075 / Phase 4.5 F5 — standard error vocabulary mapping.
 *
 * Legacy + NextAuth-native codes must normalize to the shared vocabulary and
 * every code (including unknown) must render a user message (no silent fail).
 */

const mockGet = vi.fn();
vi.mock('next/navigation', () => ({
  useSearchParams: () => ({ get: mockGet }),
}));

vi.mock('@/features/auth/model/auth-context', () => ({
  useAuth: () => ({ login: vi.fn(), isLoading: false }),
}));

const ROLE_DENIED = 'operator 계정으로는 web-store 에 접근할 수 없습니다. 운영자 콘솔을 이용해 주세요.';
const CONFIG_ERROR = '인증 서버 설정에 문제가 있습니다. 잠시 후 다시 시도해 주세요.';
const ACCESS_DENIED = '로그인이 거부되었습니다. 권한을 확인해 주세요.';
const GENERIC = '로그인 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.';
const PROVIDER_ERROR =
  'IAM 로그인이 끝나지 않았습니다. 이 사이트 이용에 동의하지 않으셨다면, 다시 «IAM 로그인»을 눌러 동의하면 계속할 수 있습니다.';

function renderWithError(code: string | null) {
  mockGet.mockImplementation((key: string) => (key === 'error' ? code : null));
  render(<LoginForm />);
}

describe('LoginForm — F5 standard error vocabulary', () => {
  beforeEach(() => {
    mockGet.mockReset();
  });

  it('legacy account_type_mismatch → role_denied 메시지', () => {
    renderWithError('account_type_mismatch');
    expect(screen.getByRole('alert').textContent).toBe(ROLE_DENIED);
  });

  it('NextAuth-native AccessDenied → role_denied 메시지', () => {
    renderWithError('AccessDenied');
    expect(screen.getByRole('alert').textContent).toBe(ROLE_DENIED);
  });

  it('standard role_denied 코드 직접 → role_denied 메시지', () => {
    renderWithError('role_denied');
    expect(screen.getByRole('alert').textContent).toBe(ROLE_DENIED);
  });

  it('legacy Configuration → config_error 메시지', () => {
    renderWithError('Configuration');
    expect(screen.getByRole('alert').textContent).toBe(CONFIG_ERROR);
  });

  it('standard config_error 직접 → config_error 메시지', () => {
    renderWithError('config_error');
    expect(screen.getByRole('alert').textContent).toBe(CONFIG_ERROR);
  });

  it('standard access_denied → access_denied 메시지', () => {
    renderWithError('access_denied');
    expect(screen.getByRole('alert').textContent).toBe(ACCESS_DENIED);
  });

  // TASK-BE-619 AC-2 — MEASURED value (nightly full-stack consent-decline.spec.ts, run 37094305963):
  // declining IAM's first-visit consent lands here as ?error=OAuthCallbackError. Before 619 it fell to
  // the generic «잠시 후 다시 시도»; it must not read as the operator refusal either.
  it('TASK-BE-619: NextAuth-native OAuthCallbackError(동의 거절 실측값) → provider_error 메시지 · operator 문구 아님', () => {
    renderWithError('OAuthCallbackError');
    const text = screen.getByRole('alert').textContent;
    expect(text).toBe(PROVIDER_ERROR);
    expect(text).not.toContain('operator');
    expect(text).not.toBe(GENERIC);
  });

  it('standard provider_error 직접 → provider_error 메시지', () => {
    renderWithError('provider_error');
    expect(screen.getByRole('alert').textContent).toBe(PROVIDER_ERROR);
  });

  it('알 수 없는 코드 → generic fallback (무음 실패 금지)', () => {
    renderWithError('totally_unknown_code_xyz');
    expect(screen.getByRole('alert').textContent).toBe(GENERIC);
  });

  it('error 파라미터 없음 → alert 미표시', () => {
    renderWithError(null);
    expect(screen.queryByRole('alert')).toBeNull();
  });
});
