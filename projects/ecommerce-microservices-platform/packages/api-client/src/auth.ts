export const ACCESS_TOKEN_KEY = 'accessToken';
export const REFRESH_TOKEN_KEY = 'refreshToken';

export interface AuthUser {
  userId: string;
  email: string;
  name: string;
}

export interface AuthState {
  user: AuthUser | null;
  isAuthenticated: boolean;
  isLoading: boolean;
}

interface JwtPayload {
  sub: string;
  email: string;
  name?: string;
  exp?: number;
}

export function parseJwtPayload(token: string): JwtPayload | null {
  try {
    const base64 = token.split('.')[1];
    if (!base64) return null;
    const json = atob(base64.replace(/-/g, '+').replace(/_/g, '/'));
    return JSON.parse(json);
  } catch {
    return null;
  }
}

export function getUserFromToken(): AuthUser | null {
  if (typeof window === 'undefined') return null;
  const token = localStorage.getItem(ACCESS_TOKEN_KEY);
  if (!token) return null;

  const payload = parseJwtPayload(token);
  if (!payload) return null;

  if (typeof payload.exp === 'number' && payload.exp * 1000 <= Date.now()) {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    localStorage.removeItem('cart');
    return null;
  }

  return {
    userId: payload.sub,
    email: payload.email,
    name: payload.name ?? '',
  };
}

export function saveTokens(accessToken: string, refreshToken: string): void {
  localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
  localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
}

export function clearTokens(): void {
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
}

export function getStoredAccessToken(): string | null {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem(ACCESS_TOKEN_KEY);
}

export function getStoredRefreshToken(): string | null {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem(REFRESH_TOKEN_KEY);
}

export const AUTH_ERROR_KEYS = [
  'INVALID_CREDENTIALS',
  'VALIDATION_ERROR',
  'NETWORK_ERROR',
  'EMAIL_ALREADY_EXISTS',
  'INVALID_REFRESH_TOKEN',
  'REFRESH_TOKEN_REVOKED',
] as const;

export type AuthErrorKey = (typeof AUTH_ERROR_KEYS)[number];

const DEFAULT_AUTH_ERROR_MESSAGES: Record<AuthErrorKey, string> = {
  INVALID_CREDENTIALS: '이메일 또는 비밀번호가 올바르지 않습니다.',
  VALIDATION_ERROR: '입력값을 확인해주세요.',
  NETWORK_ERROR: '네트워크 오류가 발생했습니다. 잠시 후 다시 시도해주세요.',
  EMAIL_ALREADY_EXISTS: '이미 가입된 이메일입니다.',
  INVALID_REFRESH_TOKEN: '인증이 만료되었습니다. 다시 로그인해주세요.',
  REFRESH_TOKEN_REVOKED: '인증이 만료되었습니다. 다시 로그인해주세요.',
};

export let AUTH_ERROR_MESSAGES: Record<string, string> = { ...DEFAULT_AUTH_ERROR_MESSAGES };

export function setAuthErrorMessages(messages: Partial<Record<AuthErrorKey, string>>): void {
  AUTH_ERROR_MESSAGES = { ...DEFAULT_AUTH_ERROR_MESSAGES, ...messages };
}
