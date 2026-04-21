import type { ApiClient } from '../client';
import type {
  SignupRequest,
  SignupResponse,
  LoginRequest,
  TokenResponse,
  RefreshRequest,
  LogoutRequest,
} from '@repo/types';

export function createAuthApi(client: ApiClient) {
  return {
    signup: (data: SignupRequest) =>
      client.post<SignupResponse>('/api/auth/signup', data),

    login: (data: LoginRequest) =>
      client.post<TokenResponse>('/api/auth/login', data),

    refresh: (data: RefreshRequest) =>
      client.post<TokenResponse>('/api/auth/refresh', data),

    logout: (data: LogoutRequest) =>
      client.post<void>('/api/auth/logout', data),
  };
}
