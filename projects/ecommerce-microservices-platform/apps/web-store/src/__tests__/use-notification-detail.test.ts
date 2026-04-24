import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import React from 'react';

const mockGetMyNotification = vi.fn();

vi.mock('@/features/notification/api/notification-api', () => ({
  getMyNotification: (...args: unknown[]) => mockGetMyNotification(...args),
}));

import { useNotificationDetail } from '@/features/notification/model/use-notification-detail';

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) =>
    React.createElement(QueryClientProvider, { client: queryClient }, children);
}

describe('useNotificationDetail', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('알림 상세를 성공적으로 조회한다', async () => {
    const mockData = { notificationId: 'n1', title: '알림', body: '본문', status: 'READ' };
    mockGetMyNotification.mockResolvedValueOnce(mockData);

    const { result } = renderHook(() => useNotificationDetail('n1'), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.notification).toEqual(mockData);
    expect(result.current.error).toBe('');
    expect(mockGetMyNotification).toHaveBeenCalledWith('n1');
  });

  it('notificationId가 비어있으면 쿼리를 실행하지 않는다', () => {
    renderHook(() => useNotificationDetail(''), { wrapper: createWrapper() });

    expect(mockGetMyNotification).not.toHaveBeenCalled();
  });

  it('NOTIFICATION_NOT_FOUND 에러 시 전용 메시지를 반환한다', async () => {
    mockGetMyNotification.mockRejectedValueOnce({
      name: 'ApiError',
      code: 'NOTIFICATION_NOT_FOUND',
      message: 'not found',
      status: 404,
    });

    const { result } = renderHook(() => useNotificationDetail('n1'), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.error).not.toBe(''));
    expect(result.current.error).toBe('알림을 찾을 수 없습니다.');
    expect(result.current.notification).toBeNull();
  });

  it('일반 에러 시 기본 메시지를 반환한다', async () => {
    mockGetMyNotification.mockRejectedValueOnce(new Error('network'));

    const { result } = renderHook(() => useNotificationDetail('n1'), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.error).not.toBe(''));
    expect(result.current.error).toBe('알림 정보를 불러오는데 실패했습니다.');
  });

  it('retryLoad 호출 시 refetch가 실행된다', async () => {
    mockGetMyNotification.mockResolvedValueOnce({ notificationId: 'n1' });

    const { result } = renderHook(() => useNotificationDetail('n1'), {
      wrapper: createWrapper(),
    });

    await waitFor(() => expect(result.current.notification).not.toBeNull());

    mockGetMyNotification.mockResolvedValueOnce({ notificationId: 'n1' });
    await result.current.retryLoad();

    expect(mockGetMyNotification).toHaveBeenCalledTimes(2);
  });
});
