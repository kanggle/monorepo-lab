import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { SignupForm } from '@/features/auth/ui/SignupForm';
import { AuthProvider } from '@/features/auth/model/auth-context';
import type { ApiErrorResponse } from '@repo/types';

const mockPush = vi.fn();
const mockReplace = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush, replace: mockReplace }),
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

const mockSignup = vi.mocked(authActions.signup);

function renderSignupForm() {
  return render(
    <AuthProvider>
      <SignupForm />
    </AuthProvider>,
  );
}

describe('SignupForm', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(Storage.prototype, 'getItem').mockReturnValue(null);
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {});
    vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => {});
  });

  it('이름, 이메일, 비밀번호 입력 필드와 회원가입 버튼을 표시한다', () => {
    renderSignupForm();

    expect(screen.getByLabelText('이름')).toBeInTheDocument();
    expect(screen.getByLabelText('이메일')).toBeInTheDocument();
    expect(screen.getByLabelText('비밀번호')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '회원가입' })).toBeInTheDocument();
  });

  it('로그인 링크를 표시한다', () => {
    renderSignupForm();

    const link = screen.getByText('로그인');
    expect(link).toHaveAttribute('href', '/login');
  });

  it('유효하지 않은 입력이면 버튼이 비활성화된다', () => {
    renderSignupForm();

    expect(screen.getByRole('button', { name: '회원가입' })).toBeDisabled();
  });

  it('유효한 입력이면 버튼이 활성화된다', async () => {
    const user = userEvent.setup();
    renderSignupForm();

    await user.type(screen.getByLabelText('이름'), 'Tester');
    await user.type(screen.getByLabelText('이메일'), 'test@test.com');
    await user.type(screen.getByLabelText('비밀번호'), 'Password123!');

    expect(screen.getByRole('button', { name: '회원가입' })).toBeEnabled();
  });

  it('회원가입 성공 시 로그인 페이지로 이동한다', async () => {
    mockSignup.mockResolvedValueOnce({
      userId: 'new-1',
      email: 'test@test.com',
      name: 'Tester',
      createdAt: '2026-03-23T00:00:00Z',
    });

    const user = userEvent.setup();
    renderSignupForm();

    await user.type(screen.getByLabelText('이름'), 'Tester');
    await user.type(screen.getByLabelText('이메일'), 'test@test.com');
    await user.type(screen.getByLabelText('비밀번호'), 'Password123!');
    await user.click(screen.getByRole('button', { name: '회원가입' }));

    await waitFor(() => {
      expect(mockPush).toHaveBeenCalledWith('/login');
    });
  });

  it('EMAIL_ALREADY_EXISTS 에러 시 에러 메시지를 표시한다', async () => {
    const apiError: ApiErrorResponse = {
      code: 'EMAIL_ALREADY_EXISTS',
      message: 'Email already exists',
      timestamp: new Date().toISOString(),
    };
    mockSignup.mockRejectedValueOnce(apiError);

    const user = userEvent.setup();
    renderSignupForm();

    await user.type(screen.getByLabelText('이름'), 'Tester');
    await user.type(screen.getByLabelText('이메일'), 'test@test.com');
    await user.type(screen.getByLabelText('비밀번호'), 'Password123!');
    await user.click(screen.getByRole('button', { name: '회원가입' }));

    await waitFor(() => {
      expect(screen.getByRole('alert')).toHaveTextContent('이미 사용 중인 이메일입니다.');
    });
  });

  it('이름이 공백만 있으면 버튼이 비활성화된다', async () => {
    const user = userEvent.setup();
    renderSignupForm();

    await user.type(screen.getByLabelText('이름'), '   ');
    await user.type(screen.getByLabelText('이메일'), 'test@test.com');
    await user.type(screen.getByLabelText('비밀번호'), 'Password123!');

    expect(screen.getByRole('button', { name: '회원가입' })).toBeDisabled();
  });

  it('비밀번호가 8자 미만이면 버튼이 비활성화된다', async () => {
    const user = userEvent.setup();
    renderSignupForm();

    await user.type(screen.getByLabelText('이름'), 'Tester');
    await user.type(screen.getByLabelText('이메일'), 'test@test.com');
    await user.type(screen.getByLabelText('비밀번호'), 'short');

    expect(screen.getByRole('button', { name: '회원가입' })).toBeDisabled();
  });
});
