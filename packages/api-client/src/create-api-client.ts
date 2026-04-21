import { ApiClient } from './client';
import {
  getStoredAccessToken,
  getStoredRefreshToken,
  saveTokens,
  clearTokens,
} from './auth';

export interface CreateApiClientOptions {
  baseURL: string;
  loginPath: string;
}

export function createApiClient({ baseURL, loginPath }: CreateApiClientOptions): ApiClient {
  return new ApiClient({
    baseURL,
    getAccessToken: () => getStoredAccessToken(),
    getRefreshToken: () => getStoredRefreshToken(),
    onTokenRefreshed: (accessToken, refreshToken) => {
      saveTokens(accessToken, refreshToken);
    },
    onAuthError: () => {
      if (typeof window !== 'undefined') {
        clearTokens();
        try {
          localStorage.removeItem('cart');
        } catch {
          // localStorage 접근 실패(프라이빗 모드 등) 시 무시하고 리다이렉트 진행
        }
        window.location.href = loginPath;
      }
    },
  });
}
