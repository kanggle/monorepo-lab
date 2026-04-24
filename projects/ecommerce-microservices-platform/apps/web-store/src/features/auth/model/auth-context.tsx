'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import type { SignupRequest, LoginRequest } from '@repo/types';
import {
  getUserFromToken,
  saveTokens,
  clearTokens,
  getStoredRefreshToken,
  type AuthState,
} from '@repo/api-client';
import { AuthContext } from '@/shared/lib/auth-context';
import * as authActions from '../api/auth-actions';

export { useAuth } from '@/shared/lib/auth-context';

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [state, setState] = useState<AuthState>({
    user: null,
    isAuthenticated: false,
    isLoading: true,
  });

  useEffect(() => {
    const user = getUserFromToken();
    setState({
      user,
      isAuthenticated: user !== null,
      isLoading: false,
    });
  }, []);

  const login = useCallback(async (data: LoginRequest) => {
    const response = await authActions.login(data);
    saveTokens(response.accessToken, response.refreshToken);

    const user = getUserFromToken();
    setState({
      user,
      isAuthenticated: user !== null,
      isLoading: false,
    });
  }, []);

  const signup = useCallback(async (data: SignupRequest) => {
    await authActions.signup(data);
  }, []);

  const logout = useCallback(async () => {
    const refreshToken = getStoredRefreshToken();
    clearTokens();
    setState({ user: null, isAuthenticated: false, isLoading: false });
    if (refreshToken) {
      try {
        await authActions.logout({ refreshToken });
      } catch {
        // 로그아웃 API 실패해도 로컬 상태는 이미 정리됨
      }
    }
  }, []);

  const value = useMemo(
    () => ({ ...state, login, signup, logout }),
    [state, login, signup, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
