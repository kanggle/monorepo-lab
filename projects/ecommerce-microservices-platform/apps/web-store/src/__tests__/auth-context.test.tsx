import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useSession, signIn, signOut } from 'next-auth/react';
import { AuthProvider, useAuth } from '@/features/auth/model/auth-context';
import { getAccessToken } from '@/shared/auth/token-bridge';

const mockUseSession = vi.mocked(useSession);
const mockSignIn = vi.mocked(signIn);
const mockSignOut = vi.mocked(signOut);

function TestConsumer() {
  const { user, isAuthenticated, isLoading, login, logout } = useAuth();
  return (
    <div>
      <span data-testid="loading">{String(isLoading)}</span>
      <span data-testid="authenticated">{String(isAuthenticated)}</span>
      <span data-testid="user">{user ? JSON.stringify(user) : 'null'}</span>
      <button onClick={() => login()}>login</button>
      <button onClick={() => logout()}>logout</button>
    </div>
  );
}

describe('AuthContext (NextAuth + GAP)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('초기 상태: useSession.status=loading 이면 isLoading=true', () => {
    mockUseSession.mockReturnValue({
      data: null,
      status: 'loading',
      update: vi.fn(),
    } as ReturnType<typeof useSession>);

    render(
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>,
    );

    expect(screen.getByTestId('loading').textContent).toBe('true');
    expect(screen.getByTestId('authenticated').textContent).toBe('false');
  });

  it('unauthenticated: isAuthenticated=false 이고 user=null', () => {
    mockUseSession.mockReturnValue({
      data: null,
      status: 'unauthenticated',
      update: vi.fn(),
    } as ReturnType<typeof useSession>);

    render(
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>,
    );

    expect(screen.getByTestId('loading').textContent).toBe('false');
    expect(screen.getByTestId('authenticated').textContent).toBe('false');
    expect(screen.getByTestId('user').textContent).toBe('null');
  });

  it('authenticated CONSUMER: user/accountId/accessToken 가 노출된다', async () => {
    mockUseSession.mockReturnValue({
      data: {
        accountId: 'acc-1',
        tenantId: 'ecommerce',
        roles: ['CUSTOMER'],
        accessToken: 'gap-access-token',
        accountType: 'CONSUMER',
        user: { email: 'consumer@test.com', name: '소비자' },
        expires: '2099-01-01T00:00:00Z',
      },
      status: 'authenticated',
      update: vi.fn(),
    } as ReturnType<typeof useSession>);

    render(
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId('authenticated').textContent).toBe('true');
    });

    expect(screen.getByTestId('user').textContent).toContain('acc-1');
    expect(screen.getByTestId('user').textContent).toContain('consumer@test.com');
    // 토큰 브리지에 access token 이 푸시됨
    expect(getAccessToken()).toBe('gap-access-token');
  });

  it('login() 호출 시 signIn("gap", {callbackUrl:"/"}) 가 호출된다', async () => {
    mockUseSession.mockReturnValue({
      data: null,
      status: 'unauthenticated',
      update: vi.fn(),
    } as ReturnType<typeof useSession>);

    const user = userEvent.setup();
    render(
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>,
    );

    await user.click(screen.getByText('login'));
    expect(mockSignIn).toHaveBeenCalledWith('gap', { callbackUrl: '/' });
  });

  it('logout() 호출 시 signOut 호출 + 토큰 브리지 정리', async () => {
    mockUseSession.mockReturnValue({
      data: {
        accountId: 'acc-1',
        accessToken: 'tok',
        accountType: 'CONSUMER',
        user: { email: 'c@test.com', name: 'c' },
        expires: '2099-01-01T00:00:00Z',
      },
      status: 'authenticated',
      update: vi.fn(),
    } as ReturnType<typeof useSession>);

    const user = userEvent.setup();
    render(
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>,
    );

    await user.click(screen.getByText('logout'));

    await waitFor(() => {
      expect(mockSignOut).toHaveBeenCalledWith({ callbackUrl: '/' });
    });
  });

  it('useAuth 를 AuthProvider 없이 사용하면 에러가 발생한다', () => {
    expect(() => {
      render(<TestConsumer />);
    }).toThrow('useAuth must be used within an AuthProvider');
  });
});
