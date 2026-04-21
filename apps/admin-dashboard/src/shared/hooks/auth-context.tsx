'use client';

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';
import type { LoginRequest } from '@repo/types';
import {
  createAuthApi,
  getUserFromToken,
  saveTokens,
  clearTokens,
  getStoredRefreshToken,
  type AuthState,
} from '@repo/api-client';
import { apiClient } from '@/shared/config/api';

interface AuthContextValue extends AuthState {
  login: (data: LoginRequest) => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

const authApi = createAuthApi(apiClient);

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
    const response = await authApi.login(data);
    saveTokens(response.accessToken, response.refreshToken);

    const user = getUserFromToken();
    setState({
      user,
      isAuthenticated: user !== null,
      isLoading: false,
    });
  }, []);

  const logout = useCallback(async () => {
    const refreshToken = getStoredRefreshToken();
    if (refreshToken) {
      try {
        await authApi.logout({ refreshToken });
      } catch {
        // 로그아웃 API 실패해도 로컬 토큰은 제거
      }
    }
    clearTokens();
    setState({ user: null, isAuthenticated: false, isLoading: false });
  }, []);

  const value = useMemo(
    () => ({ ...state, login, logout }),
    [state, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}

export type { AuthUser } from '@repo/api-client';
