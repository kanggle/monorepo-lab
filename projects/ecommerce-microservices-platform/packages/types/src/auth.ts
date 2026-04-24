// Auth domain types based on specs/contracts/http/auth-api.md

export interface SignupRequest {
  email: string;
  password: string;
  name: string;
}

export interface SignupResponse {
  userId: string;
  email: string;
  name: string;
  createdAt: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

export interface RefreshRequest {
  refreshToken: string;
}

export interface LogoutRequest {
  refreshToken: string;
}
