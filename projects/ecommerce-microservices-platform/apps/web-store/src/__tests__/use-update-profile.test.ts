import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import React from 'react';
import type { UpdateUserProfileRequest, UpdateUserProfileResponse } from '@repo/types';

const mockUpdateMyProfile = vi.fn();

vi.mock('@/features/user/api/user-profile-api', () => ({
  updateMyProfile: (...args: unknown[]) => mockUpdateMyProfile(...args),
}));

vi.mock('@/entities/user', () => ({
  userKeys: {
    all: ['user'] as const,
    profile: () => ['user', 'profile'] as const,
    addresses: () => ['user', 'addresses'] as const,
  },
}));

import { useUpdateProfile } from '@/features/user/model/use-update-profile';

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const Wrapper = ({ children }: { children: ReactNode }) =>
    React.createElement(QueryClientProvider, { client: queryClient }, children);
  return Wrapper;
}

const UPDATE_REQUEST: UpdateUserProfileRequest = {
  nickname: '새닉네임',
  phone: '010-9999-8888',
};

const UPDATE_RESPONSE: UpdateUserProfileResponse = {
  userId: 'user-1',
  email: 'test@example.com',
  name: '홍길동',
  nickname: '새닉네임',
  phone: '010-9999-8888',
  profileImageUrl: null,
  status: 'ACTIVE',
  updatedAt: '2026-04-05T00:00:00Z',
};

describe('useUpdateProfile', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('프로필 업데이트를 성공적으로 수행한다', async () => {
    mockUpdateMyProfile.mockResolvedValueOnce(UPDATE_RESPONSE);

    const { result } = renderHook(() => useUpdateProfile(), { wrapper: createWrapper() });

    act(() => {
      result.current.mutate(UPDATE_REQUEST);
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockUpdateMyProfile).toHaveBeenCalledWith(UPDATE_REQUEST);
    expect(result.current.data).toEqual(UPDATE_RESPONSE);
  });

  it('프로필 업데이트 실패 시 에러 상태를 반환한다', async () => {
    mockUpdateMyProfile.mockRejectedValueOnce(new Error('Update failed'));

    const { result } = renderHook(() => useUpdateProfile(), { wrapper: createWrapper() });

    act(() => {
      result.current.mutate(UPDATE_REQUEST);
    });

    await waitFor(() => expect(result.current.isError).toBe(true));

    expect(result.current.error).toBeInstanceOf(Error);
  });

  it('초기 상태에서 idle 상태이다', () => {
    const { result } = renderHook(() => useUpdateProfile(), { wrapper: createWrapper() });

    expect(result.current.isPending).toBe(false);
    expect(result.current.isSuccess).toBe(false);
    expect(result.current.isError).toBe(false);
  });

  it('성공 시 프로필 쿼리를 무효화한다', async () => {
    mockUpdateMyProfile.mockResolvedValueOnce(UPDATE_RESPONSE);

    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');

    const wrapper = ({ children }: { children: ReactNode }) =>
      React.createElement(QueryClientProvider, { client: queryClient }, children);

    const { result } = renderHook(() => useUpdateProfile(), { wrapper });

    act(() => {
      result.current.mutate(UPDATE_REQUEST);
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(invalidateSpy).toHaveBeenCalledWith({
      queryKey: ['user', 'profile'],
    });
  });
});
