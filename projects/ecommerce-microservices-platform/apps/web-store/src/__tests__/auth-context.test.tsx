import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AuthProvider, useAuth } from '@/features/auth/model/auth-context';

vi.mock('@/features/auth/api/auth-actions', () => ({
  login: vi.fn(),
  signup: vi.fn(),
  logout: vi.fn(),
}));

import * as authActions from '@/features/auth/api/auth-actions';

const mockLogin = vi.mocked(authActions.login);
const mockSignup = vi.mocked(authActions.signup);
const mockLogout = vi.mocked(authActions.logout);

// JWT 페이로드: { sub: 'user-1', email: 'test@test.com', name: 'Tester' }
const MOCK_ACCESS_TOKEN =
  'eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.' +
  btoa(JSON.stringify({ sub: 'user-1', email: 'test@test.com', name: 'Tester' })) +
  '.signature';
const MOCK_REFRESH_TOKEN = 'refresh-token-123';

function TestConsumer() {
  const { user, isAuthenticated, isLoading, login, signup, logout } = useAuth();
  return (
    <div>
      <span data-testid="loading">{String(isLoading)}</span>
      <span data-testid="authenticated">{String(isAuthenticated)}</span>
      <span data-testid="user">{user ? JSON.stringify(user) : 'null'}</span>
      <button onClick={() => login({ email: 'test@test.com', password: 'password123' })}>
        login
      </button>
      <button onClick={() => signup({ email: 'new@test.com', password: 'password123', name: 'New' })}>
        signup
      </button>
      <button onClick={() => logout()}>logout</button>
    </div>
  );
}

describe('AuthContext', () => {
  let storage: Record<string, string>;

  beforeEach(() => {
    vi.clearAllMocks();
    storage = {};
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation((key) => storage[key] ?? null);
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation((key, value) => {
      storage[key] = value;
    });
    vi.spyOn(Storage.prototype, 'removeItem').mockImplementation((key) => {
      delete storage[key];
    });
  });

  it('초기 상태에서 토큰이 없으면 미인증 상태이다', async () => {
    render(
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId('loading').textContent).toBe('false');
    });
    expect(screen.getByTestId('authenticated').textContent).toBe('false');
    expect(screen.getByTestId('user').textContent).toBe('null');
  });

  it('localStorage에 유효한 토큰이 있으면 인증 상태이다', async () => {
    storage['accessToken'] = MOCK_ACCESS_TOKEN;

    render(
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId('loading').textContent).toBe('false');
    });
    expect(screen.getByTestId('authenticated').textContent).toBe('true');
    expect(screen.getByTestId('user').textContent).toContain('user-1');
  });

  it('login 호출 시 토큰을 저장하고 인증 상태가 된다', async () => {
    mockLogin.mockResolvedValueOnce({
      accessToken: MOCK_ACCESS_TOKEN,
      refreshToken: MOCK_REFRESH_TOKEN,
      expiresIn: 3600,
    });

    const user = userEvent.setup();
    render(
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId('loading').textContent).toBe('false');
    });

    await user.click(screen.getByText('login'));

    await waitFor(() => {
      expect(screen.getByTestId('authenticated').textContent).toBe('true');
    });
    expect(storage['accessToken']).toBe(MOCK_ACCESS_TOKEN);
    expect(storage['refreshToken']).toBe(MOCK_REFRESH_TOKEN);
  });

  it('signup 호출 시 authActions.signup을 호출한다', async () => {
    mockSignup.mockResolvedValueOnce({
      userId: 'new-1',
      email: 'new@test.com',
      name: 'New',
      createdAt: '2026-03-23T00:00:00Z',
    });

    const user = userEvent.setup();
    render(
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId('loading').textContent).toBe('false');
    });

    await user.click(screen.getByText('signup'));

    await waitFor(() => {
      expect(mockSignup).toHaveBeenCalledWith({
        email: 'new@test.com',
        password: 'password123',
        name: 'New',
      });
    });
    // signup은 토큰을 저장하지 않는다
    expect(screen.getByTestId('authenticated').textContent).toBe('false');
  });

  it('logout 호출 시 토큰을 제거하고 미인증 상태가 된다', async () => {
    storage['accessToken'] = MOCK_ACCESS_TOKEN;
    storage['refreshToken'] = MOCK_REFRESH_TOKEN;
    mockLogout.mockResolvedValueOnce(undefined);

    const user = userEvent.setup();
    render(
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId('authenticated').textContent).toBe('true');
    });

    await user.click(screen.getByText('logout'));

    await waitFor(() => {
      expect(screen.getByTestId('authenticated').textContent).toBe('false');
    });
    expect(storage['accessToken']).toBeUndefined();
    expect(storage['refreshToken']).toBeUndefined();
  });

  it('logout API 실패해도 로컬 토큰은 제거된다', async () => {
    storage['accessToken'] = MOCK_ACCESS_TOKEN;
    storage['refreshToken'] = MOCK_REFRESH_TOKEN;
    mockLogout.mockRejectedValueOnce(new Error('network error'));

    const user = userEvent.setup();
    render(
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId('authenticated').textContent).toBe('true');
    });

    await user.click(screen.getByText('logout'));

    await waitFor(() => {
      expect(screen.getByTestId('authenticated').textContent).toBe('false');
    });
    expect(storage['accessToken']).toBeUndefined();
  });

  it('useAuth를 AuthProvider 없이 사용하면 에러가 발생한다', () => {
    expect(() => {
      render(<TestConsumer />);
    }).toThrow('useAuth must be used within an AuthProvider');
  });
});
