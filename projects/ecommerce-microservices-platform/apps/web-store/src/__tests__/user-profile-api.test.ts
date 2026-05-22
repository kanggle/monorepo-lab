import { describe, it, expect, vi, beforeEach } from 'vitest';

const { mockGetMe, mockUpdateMe } = vi.hoisted(() => ({
  mockGetMe: vi.fn(),
  mockUpdateMe: vi.fn(),
}));

vi.mock('@/shared/config/api', () => ({
  apiClient: {},
}));

vi.mock('@repo/api-client', () => ({
  createUserApi: vi.fn(() => ({
    getMe: mockGetMe,
    updateMe: mockUpdateMe,
  })),
}));

describe('user-profile-api', () => {
  beforeEach(async () => {
    vi.clearAllMocks();
    vi.resetModules();
  });

  async function loadModule() {
    const mod = await import('@/features/user/api/user-profile-api');
    return mod;
  }

  describe('getMyProfile', () => {
    it('API에서 사용자 프로필을 정상적으로 조회한다', async () => {
      const profile = {
        userId: 'user-1',
        email: 'user@example.com',
        name: '홍길동',
        nickname: '길동이',
        phone: '010-1234-5678',
        profileImageUrl: null,
        status: 'ACTIVE',
        createdAt: '2026-01-01T00:00:00Z',
        updatedAt: '2026-01-01T00:00:00Z',
      };
      mockGetMe.mockResolvedValueOnce(profile);

      const { getMyProfile } = await loadModule();
      const result = await getMyProfile();

      expect(mockGetMe).toHaveBeenCalled();
      expect(result).toEqual(profile);
    });

    it('API 에러 시 에러를 그대로 전파한다', async () => {
      mockGetMe.mockRejectedValueOnce(new Error('Unauthorized'));

      const { getMyProfile } = await loadModule();

      await expect(getMyProfile()).rejects.toThrow('Unauthorized');
    });

    it('정상 응답의 userId는 mock-user가 아니다', async () => {
      const profile = {
        userId: 'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
        email: 'user@example.com',
        name: '홍길동',
        nickname: null,
        phone: null,
        profileImageUrl: null,
        status: 'ACTIVE',
        createdAt: '2026-01-01T00:00:00Z',
        updatedAt: '2026-01-01T00:00:00Z',
      };
      mockGetMe.mockResolvedValueOnce(profile);

      const { getMyProfile } = await loadModule();
      const result = await getMyProfile();

      expect(result.userId).not.toBe('mock-user');
      expect(result.userId).toBe('a1b2c3d4-e5f6-7890-abcd-ef1234567890');
    });
  });

  describe('updateMyProfile', () => {
    it('API를 통해 프로필을 정상적으로 업데이트한다', async () => {
      // The PATCH contract is omit-to-skip (Request fields are `string`, not `string | null`).
      // To "keep the existing value", omit the field; we test only fields actually being updated.
      const updateData = { nickname: '새닉네임', phone: '010-9999-8888' };
      const response = {
        userId: 'user-1',
        email: 'user@example.com',
        name: '홍길동',
        nickname: '새닉네임',
        phone: '010-9999-8888',
        profileImageUrl: null,
        status: 'ACTIVE',
        updatedAt: '2026-04-05T00:00:00Z',
      };
      mockUpdateMe.mockResolvedValueOnce(response);

      const { updateMyProfile } = await loadModule();
      const result = await updateMyProfile(updateData);

      expect(mockUpdateMe).toHaveBeenCalledWith(updateData);
      expect(result).toEqual(response);
    });

    it('API 에러 시 에러를 그대로 전파한다', async () => {
      mockUpdateMe.mockRejectedValueOnce(new Error('Server error'));

      const { updateMyProfile } = await loadModule();

      await expect(
        updateMyProfile({ nickname: '오프라인닉네임', phone: '010-0000-0000', profileImageUrl: 'img.jpg' }),
      ).rejects.toThrow('Server error');
    });

    it('업데이트 응답의 userId는 mock-user가 아니다', async () => {
      // PATCH omit-to-skip semantics — only set nickname.
      const updateData = { nickname: '새닉네임' };
      const response = {
        userId: 'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
        email: 'user@example.com',
        name: '홍길동',
        nickname: '새닉네임',
        phone: null,
        profileImageUrl: null,
        status: 'ACTIVE',
        updatedAt: '2026-04-05T00:00:00Z',
      };
      mockUpdateMe.mockResolvedValueOnce(response);

      const { updateMyProfile } = await loadModule();
      const result = await updateMyProfile(updateData);

      expect(result.userId).not.toBe('mock-user');
    });
  });
});
