import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useSession, signIn } from 'next-auth/react';
import { LoginForm } from '@/features/auth/components/LoginForm';
import { AuthProvider } from '@/shared/hooks';

const mockReplace = vi.fn();
const mockPush = vi.fn();
const mockSearchParams = { value: new URLSearchParams() };

vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: mockReplace, push: mockPush }),
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

describe('LoginForm (admin-dashboard, GAP)', () => {
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

  it('?error=account_type_mismatch 가 있으면 안내 메시지를 표시한다', () => {
    mockSearchParams.value = new URLSearchParams('error=account_type_mismatch');
    renderLoginForm();
    expect(screen.getByRole('alert')).toHaveTextContent('consumer 계정으로는 admin');
  });

  it('?error 가 없으면 alert 가 표시되지 않는다', () => {
    renderLoginForm();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('버튼 클릭 시 signIn("iam", {callbackUrl: "/dashboard"}) 호출', async () => {
    const user = userEvent.setup();
    renderLoginForm();
    await user.click(screen.getByRole('button', { name: 'Global Account 로 로그인' }));
    await waitFor(() => {
      expect(mockSignIn).toHaveBeenCalledWith('iam', { callbackUrl: '/dashboard' });
    });
  });

  it('?from=/orders 가 있으면 callbackUrl 로 전달된다', async () => {
    mockSearchParams.value = new URLSearchParams('from=%2Forders');
    const user = userEvent.setup();
    renderLoginForm();
    await user.click(screen.getByRole('button', { name: 'Global Account 로 로그인' }));
    await waitFor(() => {
      expect(mockSignIn).toHaveBeenCalledWith('iam', { callbackUrl: '/orders' });
    });
  });

  it('이미 인증되어 있으면 callbackUrl 로 replace 한다', () => {
    mockUseSession.mockReturnValue({
      data: {
        accountId: 'op-1',
        accountType: 'OPERATOR',
        user: { email: 'op@test.com', name: 'Operator' },
        expires: '2099-01-01T00:00:00Z',
      },
      status: 'authenticated',
      update: vi.fn(),
    } as ReturnType<typeof useSession>);

    renderLoginForm();
    expect(mockReplace).toHaveBeenCalledWith('/dashboard');
  });
});
