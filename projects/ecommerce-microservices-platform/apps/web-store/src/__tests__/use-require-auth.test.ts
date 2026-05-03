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
import { useRequireAuth } from '@/features/auth/model/use-require-auth';

const mockUseAuth = vi.mocked(useAuth);

const baseAuth = {
  user: null,
  isAuthenticated: false,
  isLoading: false,
  login: vi.fn(),
  logout: vi.fn(),
};

describe('useRequireAuth', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('로딩 중이면 isReady=false 이고 리다이렉트하지 않는다', () => {
    mockUseAuth.mockReturnValue({ ...baseAuth, isLoading: true });

    const { result } = renderHook(() => useRequireAuth());

    expect(result.current.isReady).toBe(false);
    expect(mockReplace).not.toHaveBeenCalled();
  });

  it('인증되지 않은 사용자는 /login 으로 리다이렉트된다', async () => {
    mockUseAuth.mockReturnValue(baseAuth);

    const { result } = renderHook(() => useRequireAuth());

    await waitFor(() => {
      expect(mockReplace).toHaveBeenCalledWith('/login');
    });
    expect(result.current.isReady).toBe(false);
  });

  it('인증된 사용자는 리다이렉트되지 않고 isReady=true', () => {
    mockUseAuth.mockReturnValue({
      ...baseAuth,
      isAuthenticated: true,
      user: { userId: 'u1', email: 'a@b.com', name: 'A' },
    });

    const { result } = renderHook(() => useRequireAuth());

    expect(result.current.isReady).toBe(true);
    expect(mockReplace).not.toHaveBeenCalled();
  });
});
