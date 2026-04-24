import { apiClient } from '@/shared/config/api';
import { createAuthApi } from '@repo/api-client';
import type {
  SignupRequest,
  SignupResponse,
  LoginRequest,
  TokenResponse,
  LogoutRequest,
} from '@repo/types';

const authApi = createAuthApi(apiClient);

export async function signup(data: SignupRequest): Promise<SignupResponse> {
  return authApi.signup(data);
}

export async function login(data: LoginRequest): Promise<TokenResponse> {
  return authApi.login(data);
}

export async function logout(data: LogoutRequest): Promise<void> {
  return authApi.logout(data);
}
