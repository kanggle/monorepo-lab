import { describe, it, expect, vi, beforeEach } from 'vitest';

const { mockSignup, mockLogin, mockLogout } = vi.hoisted(() => ({
  mockSignup: vi.fn(),
  mockLogin: vi.fn(),
  mockLogout: vi.fn(),
}));

vi.mock('@repo/api-client', () => ({
  createAuthApi: () => ({
    signup: mockSignup,
    login: mockLogin,
    logout: mockLogout,
  }),
}));

vi.mock('@/shared/config/api', () => ({
  apiClient: {},
}));

import { signup, login, logout } from '@/features/auth/api/auth-actions';

describe('auth-actions', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('signup', () => {
    it('회원가입 요청을 성공적으로 처리한다', async () => {
      const request = { email: 'test@test.com', password: 'password123', name: 'Tester' };
      const response = {
        userId: 'user-1',
        email: 'test@test.com',
        name: 'Tester',
        createdAt: '2026-04-05T00:00:00Z',
      };
      mockSignup.mockResolvedValueOnce(response);

      const result = await signup(request);

      expect(mockSignup).toHaveBeenCalledWith(request);
      expect(result).toEqual(response);
    });

    it('회원가입 API 호출이 실패하면 에러가 전파된다', async () => {
      const request = { email: 'test@test.com', password: 'password123', name: 'Tester' };
      mockSignup.mockRejectedValueOnce(new Error('Email already exists'));

      await expect(signup(request)).rejects.toThrow('Email already exists');
    });
  });

  describe('login', () => {
    it('로그인 요청을 성공적으로 처리하고 토큰을 반환한다', async () => {
      const request = { email: 'test@test.com', password: 'password123' };
      const response = {
        accessToken: 'access-token-123',
        refreshToken: 'refresh-token-123',
        expiresIn: 3600,
      };
      mockLogin.mockResolvedValueOnce(response);

      const result = await login(request);

      expect(mockLogin).toHaveBeenCalledWith(request);
      expect(result).toEqual(response);
    });

    it('로그인 API 호출이 실패하면 에러가 전파된다', async () => {
      const request = { email: 'test@test.com', password: 'wrong-password' };
      mockLogin.mockRejectedValueOnce(new Error('Invalid credentials'));

      await expect(login(request)).rejects.toThrow('Invalid credentials');
    });
  });

  describe('logout', () => {
    it('로그아웃 요청을 성공적으로 처리한다', async () => {
      const request = { refreshToken: 'refresh-token-123' };
      mockLogout.mockResolvedValueOnce(undefined);

      await logout(request);

      expect(mockLogout).toHaveBeenCalledWith(request);
    });

    it('로그아웃 API 호출이 실패하면 에러가 전파된다', async () => {
      const request = { refreshToken: 'refresh-token-123' };
      mockLogout.mockRejectedValueOnce(new Error('Network error'));

      await expect(logout(request)).rejects.toThrow('Network error');
    });
  });
});
