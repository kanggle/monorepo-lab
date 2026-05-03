import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useSession, signIn } from 'next-auth/react';
import { LoginForm } from '@/features/auth/ui/LoginForm';
import { AuthProvider } from '@/features/auth/model/auth-context';

const mockSearchParams = { value: new URLSearchParams() };

vi.mock('next/navigation', () => ({
  useSearchParams: () => mockSearchParams.value,
}));

const mockUseSession = vi.mocked(useSession);
const mockSignIn = vi.mocked(signIn);

function renderLoginForm() {
  return render(
    <AuthProvider>
      <LoginForm />
    </AuthProvider>,
  );
}

describe('LoginForm (GAP)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockSearchParams.value = new URLSearchParams();
    mockUseSession.mockReturnValue({
      data: null,
      status: 'unauthenticated',
      update: vi.fn(),
    } as ReturnType<typeof useSession>);
  });

  it('GAP 로그인 버튼을 표시한다', () => {
    renderLoginForm();
    expect(screen.getByRole('button', { name: 'Global Account 로 로그인' })).toBeInTheDocument();
  });

  it('회원가입 링크를 표시한다', () => {
    renderLoginForm();
    expect(screen.getByText('회원가입').closest('a')).toHaveAttribute('href', '/signup');
  });

  it('버튼 클릭 시 signIn("gap", {callbackUrl: "/"}) 가 호출된다', async () => {
    const user = userEvent.setup();
    renderLoginForm();

    await user.click(screen.getByRole('button', { name: 'Global Account 로 로그인' }));

    await waitFor(() => {
      expect(mockSignIn).toHaveBeenCalledWith('gap', { callbackUrl: '/' });
    });
  });

  it('?from=/products/p1 이 있으면 callbackUrl 로 전달한다', async () => {
    mockSearchParams.value = new URLSearchParams('from=%2Fproducts%2Fp1');

    const user = userEvent.setup();
    renderLoginForm();
    await user.click(screen.getByRole('button', { name: 'Global Account 로 로그인' }));

    await waitFor(() => {
      expect(mockSignIn).toHaveBeenCalledWith('gap', { callbackUrl: '/products/p1' });
    });
  });

  it('외부 URL from 은 무시하고 / 로 폴백한다', async () => {
    mockSearchParams.value = new URLSearchParams('from=https%3A%2F%2Fevil.com');
    const user = userEvent.setup();
    renderLoginForm();
    await user.click(screen.getByRole('button', { name: 'Global Account 로 로그인' }));

    await waitFor(() => {
      expect(mockSignIn).toHaveBeenCalledWith('gap', { callbackUrl: '/' });
    });
  });

  it('?error=account_type_mismatch 가 있으면 안내 메시지를 표시한다', () => {
    mockSearchParams.value = new URLSearchParams('error=account_type_mismatch');
    renderLoginForm();

    expect(screen.getByRole('alert')).toHaveTextContent(/admin 계정으로는 web-store/);
  });

  it('?error=Configuration 이면 인증 서버 안내 메시지를 표시한다', () => {
    mockSearchParams.value = new URLSearchParams('error=Configuration');
    renderLoginForm();

    expect(screen.getByRole('alert')).toHaveTextContent(/인증 서버 설정/);
  });
});
