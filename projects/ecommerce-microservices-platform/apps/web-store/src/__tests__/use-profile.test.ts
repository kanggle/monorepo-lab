import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import React from 'react';
import type { UserProfile } from '@repo/types';

const mockGetMyProfile = vi.fn();

vi.mock('@/features/user/api/user-profile-api', () => ({
  getMyProfile: (...args: unknown[]) => mockGetMyProfile(...args),
}));

vi.mock('@/entities/user', () => ({
  userKeys: {
    all: ['user'] as const,
    profile: () => ['user', 'profile'] as const,
    addresses: () => ['user', 'addresses'] as const,
  },
}));

import { useProfile } from '@/features/user/model/use-profile';

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) =>
    React.createElement(QueryClientProvider, { client: queryClient }, children);
}

const MOCK_PROFILE: UserProfile = {
  userId: 'user-1',
  email: 'test@example.com',
  name: '홍길동',
  nickname: '길동이',
  phone: '010-1234-5678',
  profileImageUrl: null,
  status: 'ACTIVE',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
};

describe('useProfile', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('프로필 정보를 성공적으로 조회한다', async () => {
    mockGetMyProfile.mockResolvedValueOnce(MOCK_PROFILE);

    const { result } = renderHook(() => useProfile(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toEqual(MOCK_PROFILE);
    expect(mockGetMyProfile).toHaveBeenCalledTimes(1);
  });

  it('프로필 조회 실패 시 에러 상태를 반환한다', async () => {
    mockGetMyProfile.mockRejectedValueOnce(new Error('Unauthorized'));

    const { result } = renderHook(() => useProfile(), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(result.current.error).toBeInstanceOf(Error);
  });

  it('로딩 중 isLoading이 true를 반환한다', () => {
    mockGetMyProfile.mockReturnValue(new Promise(() => {}));

    const { result } = renderHook(() => useProfile(), { wrapper: createWrapper() });

    expect(result.current.isLoading).toBe(true);
    expect(result.current.data).toBeUndefined();
  });
});
