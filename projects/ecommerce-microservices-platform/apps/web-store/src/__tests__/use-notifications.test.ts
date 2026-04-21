import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import React from 'react';

const mockGetMyNotifications = vi.fn();

vi.mock('@/features/notification/api/notification-api', () => ({
  getMyNotifications: (...args: unknown[]) => mockGetMyNotifications(...args),
}));

import { useNotifications } from '@/features/notification/model/use-notifications';

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) =>
    React.createElement(QueryClientProvider, { client: queryClient }, children);
}

describe('useNotifications', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('알림 목록을 성공적으로 조회한다', async () => {
    const mockData = {
      content: [{ notificationId: 'n1', title: '알림', status: 'UNREAD' }],
      page: 0,
      size: 10,
      totalElements: 1,
    };
    mockGetMyNotifications.mockResolvedValueOnce(mockData);

    const { result } = renderHook(() => useNotifications(0, 10), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toEqual(mockData);
    expect(mockGetMyNotifications).toHaveBeenCalledWith(0, 10);
  });

  it('페이지와 사이즈 파라미터를 API에 전달한다', async () => {
    mockGetMyNotifications.mockResolvedValueOnce({ content: [], page: 3, size: 5, totalElements: 0 });

    const { result } = renderHook(() => useNotifications(3, 5), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(mockGetMyNotifications).toHaveBeenCalledWith(3, 5);
  });

  it('API 실패 시 에러 상태를 반환한다', async () => {
    mockGetMyNotifications.mockRejectedValueOnce(new Error('fail'));

    const { result } = renderHook(() => useNotifications(0, 10), { wrapper: createWrapper() });

    await waitFor(() => expect(result.current.isError).toBe(true));
  });
});
