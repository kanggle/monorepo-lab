import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';

const mockReplace = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: mockReplace }),
}));

vi.mock('@/features/auth/model/auth-context', () => ({
  useAuth: vi.fn(),
}));

import { useAuth } from '@/features/auth/model/auth-context';
import { useRedirectIfAuthenticated } from '@/features/auth/model/use-redirect-if-authenticated';

const mockUseAuth = vi.mocked(useAuth);

describe('useRedirectIfAuthenticated', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('로딩 중일 때 isReady는 false이고 리다이렉트하지 않는다', () => {
    mockUseAuth.mockReturnValue({
      isAuthenticated: false,
      isLoading: true,
      user: null,
      login: vi.fn(),
      signup: vi.fn(),
      logout: vi.fn(),
    });

    const { result } = renderHook(() => useRedirectIfAuthenticated());

    expect(result.current.isReady).toBe(false);
    expect(mockReplace).not.toHaveBeenCalled();
  });

  it('인증된 사용자는 /로 리다이렉트된다', async () => {
    mockUseAuth.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      user: { id: 'user-1', email: 'test@test.com', name: 'Tester' },
      login: vi.fn(),
      signup: vi.fn(),
      logout: vi.fn(),
    });

    const { result } = renderHook(() => useRedirectIfAuthenticated());

    await waitFor(() => {
      expect(mockReplace).toHaveBeenCalledWith('/');
    });
    expect(result.current.isReady).toBe(false);
  });

  it('인증되지 않은 사용자는 리다이렉트되지 않고 isReady가 true이다', () => {
    mockUseAuth.mockReturnValue({
      isAuthenticated: false,
      isLoading: false,
      user: null,
      login: vi.fn(),
      signup: vi.fn(),
      logout: vi.fn(),
    });

    const { result } = renderHook(() => useRedirectIfAuthenticated());

    expect(result.current.isReady).toBe(true);
    expect(mockReplace).not.toHaveBeenCalled();
  });

  it('로딩이 끝난 후 인증 상태가 변경되면 리다이렉트가 실행된다', async () => {
    mockUseAuth.mockReturnValue({
      isAuthenticated: false,
      isLoading: false,
      user: null,
      login: vi.fn(),
      signup: vi.fn(),
      logout: vi.fn(),
    });

    const { result, rerender } = renderHook(() => useRedirectIfAuthenticated());

    expect(result.current.isReady).toBe(true);
    expect(mockReplace).not.toHaveBeenCalled();

    mockUseAuth.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      user: { id: 'user-1', email: 'test@test.com', name: 'Tester' },
      login: vi.fn(),
      signup: vi.fn(),
      logout: vi.fn(),
    });

    rerender();

    await waitFor(() => {
      expect(mockReplace).toHaveBeenCalledWith('/');
    });
    expect(result.current.isReady).toBe(false);
  });

  it('로딩 중이면서 인증된 상태여도 isReady는 false이다', () => {
    mockUseAuth.mockReturnValue({
      isAuthenticated: true,
      isLoading: true,
      user: { id: 'user-1', email: 'test@test.com', name: 'Tester' },
      login: vi.fn(),
      signup: vi.fn(),
      logout: vi.fn(),
    });

    const { result } = renderHook(() => useRedirectIfAuthenticated());

    expect(result.current.isReady).toBe(false);
    expect(mockReplace).not.toHaveBeenCalled();
  });
});
