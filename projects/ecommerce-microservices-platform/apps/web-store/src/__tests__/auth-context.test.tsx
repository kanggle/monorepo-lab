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

  it('login() 호출 시 signIn("iam", {callbackUrl:"/"}) 가 호출된다', async () => {
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
    expect(mockSignIn).toHaveBeenCalledWith('iam', { callbackUrl: '/' });
  });

  it('logout() 호출 시 end_session URL 조회 → signOut(redirect:false) → GAP 리다이렉트 (RP-initiated logout)', async () => {
    mockUseSession.mockReturnValue({
      data: {
        accountId: 'acc-1',
        accessToken: 'tok',
        user: { email: 'c@test.com', name: 'c' },
        expires: '2099-01-01T00:00:00Z',
      },
      status: 'authenticated',
      update: vi.fn(),
    } as ReturnType<typeof useSession>);

    // The server route returns the GAP end_session URL; logout must navigate to
    // it so the IdP session is terminated (RP-initiated logout).
    const endSession =
      'http://iam.local/connect/logout?id_token_hint=x&post_logout_redirect_uri=http%3A%2F%2Flocalhost%3A3000%2F&client_id=ecommerce-web-store-client';
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ url: endSession }),
    });
    vi.stubGlobal('fetch', fetchMock);
    const hrefSetter = vi.fn();
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: {
        set href(v: string) {
          hrefSetter(v);
        },
      },
    });

    const user = userEvent.setup();
    render(
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>,
    );

    await user.click(screen.getByText('logout'));

    await waitFor(() => {
      expect(mockSignOut).toHaveBeenCalledWith({ redirect: false });
    });
    expect(fetchMock).toHaveBeenCalledWith('/api/auth/end-session-url', {
      cache: 'no-store',
    });
    expect(hrefSetter).toHaveBeenCalledWith(endSession);

    vi.unstubAllGlobals();
  });

  it('useAuth 를 AuthProvider 없이 사용하면 에러가 발생한다', () => {
    expect(() => {
      render(<TestConsumer />);
    }).toThrow('useAuth must be used within an AuthProvider');
  });
});
