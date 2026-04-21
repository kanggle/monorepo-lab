import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { LoginForm } from '@/features/auth/ui/LoginForm';
import { AuthProvider } from '@/features/auth/model/auth-context';
import type { ApiErrorResponse } from '@repo/types';

const mockPush = vi.fn();
const mockReplace = vi.fn();
const mockSearchParams = { value: new URLSearchParams() };

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush, replace: mockReplace }),
  useSearchParams: () => mockSearchParams.value,
}));

vi.mock('next/link', () => ({
  default: ({ href, children }: { href: string; children: React.ReactNode }) => (
    <a href={href}>{children}</a>
  ),
}));

vi.mock('@/features/auth/api/auth-actions', () => ({
  login: vi.fn(),
  signup: vi.fn(),
  logout: vi.fn(),
}));

import * as authActions from '@/features/auth/api/auth-actions';

const mockLogin = vi.mocked(authActions.login);

function renderLoginForm() {
  return render(
    <AuthProvider>
      <LoginForm />
    </AuthProvider>,
  );
}

describe('LoginForm', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(Storage.prototype, 'getItem').mockReturnValue(null);
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {});
    vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => {});
    mockSearchParams.value = new URLSearchParams();
  });

  it('이메일, 비밀번호 입력 필드와 로그인 버튼을 표시한다', () => {
    renderLoginForm();

    expect(screen.getByLabelText('이메일')).toBeInTheDocument();
    expect(screen.getByLabelText('비밀번호')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '로그인' })).toBeInTheDocument();
  });

  it('회원가입 링크를 표시한다', () => {
    renderLoginForm();

    const link = screen.getByText('회원가입');
    expect(link).toHaveAttribute('href', '/signup');
  });

  it('유효하지 않은 입력이면 버튼이 비활성화된다', () => {
    renderLoginForm();

    expect(screen.getByRole('button', { name: '로그인' })).toBeDisabled();
  });

  it('유효한 입력이면 버튼이 활성화된다', async () => {
    const user = userEvent.setup();
    renderLoginForm();

    await user.type(screen.getByLabelText('이메일'), 'test@test.com');
    await user.type(screen.getByLabelText('비밀번호'), 'password123');

    expect(screen.getByRole('button', { name: '로그인' })).toBeEnabled();
  });

  it('로그인 성공 시 홈으로 이동한다', async () => {
    mockLogin.mockResolvedValueOnce({
      accessToken: 'token',
      refreshToken: 'refresh',
      expiresIn: 3600,
    });

    const user = userEvent.setup();
    renderLoginForm();

    await user.type(screen.getByLabelText('이메일'), 'test@test.com');
    await user.type(screen.getByLabelText('비밀번호'), 'password123');
    await user.click(screen.getByRole('button', { name: '로그인' }));

    await waitFor(() => {
      expect(mockPush).toHaveBeenCalledWith('/');
    });
  });

  it('redirect 쿼리 파라미터가 있으면 해당 경로로 이동한다', async () => {
    mockSearchParams.value = new URLSearchParams('redirect=%2Fproducts%2Fp1');
    mockLogin.mockResolvedValueOnce({
      accessToken: 'token',
      refreshToken: 'refresh',
      expiresIn: 3600,
    });

    const user = userEvent.setup();
    renderLoginForm();

    await user.type(screen.getByLabelText('이메일'), 'test@test.com');
    await user.type(screen.getByLabelText('비밀번호'), 'password123');
    await user.click(screen.getByRole('button', { name: '로그인' }));

    await waitFor(() => {
      expect(mockPush).toHaveBeenCalledWith('/products/p1');
    });
  });

  it('외부 URL redirect는 무시하고 홈으로 이동한다', async () => {
    mockSearchParams.value = new URLSearchParams('redirect=https%3A%2F%2Fevil.com');
    mockLogin.mockResolvedValueOnce({
      accessToken: 'token',
      refreshToken: 'refresh',
      expiresIn: 3600,
    });

    const user = userEvent.setup();
    renderLoginForm();

    await user.type(screen.getByLabelText('이메일'), 'test@test.com');
    await user.type(screen.getByLabelText('비밀번호'), 'password123');
    await user.click(screen.getByRole('button', { name: '로그인' }));

    await waitFor(() => {
      expect(mockPush).toHaveBeenCalledWith('/');
    });
  });

  it('INVALID_CREDENTIALS 에러 시 에러 메시지를 표시한다', async () => {
    const apiError: ApiErrorResponse = {
      code: 'INVALID_CREDENTIALS',
      message: 'Invalid credentials',
      timestamp: new Date().toISOString(),
    };
    mockLogin.mockRejectedValueOnce(apiError);

    const user = userEvent.setup();
    renderLoginForm();

    await user.type(screen.getByLabelText('이메일'), 'test@test.com');
    await user.type(screen.getByLabelText('비밀번호'), 'password123');
    await user.click(screen.getByRole('button', { name: '로그인' }));

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(
        '이메일 또는 비밀번호가 올바르지 않습니다.',
      );
    });
  });

  it('NETWORK_ERROR 시 네트워크 에러 메시지를 표시한다', async () => {
    const apiError: ApiErrorResponse = {
      code: 'NETWORK_ERROR',
      message: 'Network error',
      timestamp: new Date().toISOString(),
    };
    mockLogin.mockRejectedValueOnce(apiError);

    const user = userEvent.setup();
    renderLoginForm();

    await user.type(screen.getByLabelText('이메일'), 'test@test.com');
    await user.type(screen.getByLabelText('비밀번호'), 'password123');
    await user.click(screen.getByRole('button', { name: '로그인' }));

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent(
        '네트워크 오류가 발생했습니다.',
      );
    });
  });

  it('제출 중에는 버튼이 비활성화되고 로딩 텍스트를 표시한다', async () => {
    let resolveLogin: (value: unknown) => void;
    mockLogin.mockImplementation(
      () => new Promise((resolve) => { resolveLogin = resolve; }),
    );

    const user = userEvent.setup();
    renderLoginForm();

    await user.type(screen.getByLabelText('이메일'), 'test@test.com');
    await user.type(screen.getByLabelText('비밀번호'), 'password123');
    await user.click(screen.getByRole('button', { name: '로그인' }));

    expect(screen.getByRole('button', { name: '로그인 중...' })).toBeDisabled();

    await act(async () => {
      resolveLogin!({ accessToken: 'a', refreshToken: 'b', expiresIn: 3600 });
    });
  });
});
