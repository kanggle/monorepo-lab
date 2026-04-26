import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import React from 'react';

const mockGetMyPreferences = vi.fn();

vi.mock('@/features/notification/api/notification-api', () => ({
  getMyPreferences: () => mockGetMyPreferences(),
}));

import { useNotificationPreferences } from '@/features/notification/model/use-notification-preferences';

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const Wrapper = ({ children }: { children: ReactNode }) =>
    React.createElement(QueryClientProvider, { client: queryClient }, children);
  return Wrapper;
}

describe('useNotificationPreferences', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('알림 설정을 성공적으로 조회한다', async () => {
    const mockPrefs = { email: true, push: false, sms: true };
    mockGetMyPreferences.mockResolvedValueOnce(mockPrefs);

    const { result } = renderHook(() => useNotificationPreferences(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toEqual(mockPrefs);
    expect(mockGetMyPreferences).toHaveBeenCalledTimes(1);
  });

  it('API 실패 시 에러 상태를 반환한다', async () => {
    mockGetMyPreferences.mockRejectedValueOnce(new Error('fail'));

    const { result } = renderHook(() => useNotificationPreferences(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
