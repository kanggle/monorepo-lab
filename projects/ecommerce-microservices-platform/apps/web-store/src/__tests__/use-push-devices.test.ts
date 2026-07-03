import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createElement, type ReactNode } from 'react';
import { notificationKeys } from '@/features/notification/model/query-keys';

vi.mock('@/features/notification/lib/web-push', () => ({
  isPushSupported: vi.fn(() => true),
}));

vi.mock('@/features/notification/api/push-subscription-api', () => ({
  listPushDevices: vi.fn(),
  deletePushSubscription: vi.fn(),
}));

import { usePushDevices } from '@/features/notification/model/use-push-devices';
import {
  listPushDevices,
  deletePushSubscription,
} from '@/features/notification/api/push-subscription-api';

const mockList = vi.mocked(listPushDevices);
const mockDelete = vi.mocked(deletePushSubscription);

const CURRENT = 'https://push/current';
const OTHER = 'https://push/other';

function fakeSubscription(endpoint: string) {
  return { endpoint, unsubscribe: vi.fn().mockResolvedValue(true) };
}

function setServiceWorker(subscription: unknown) {
  Object.defineProperty(navigator, 'serviceWorker', {
    configurable: true,
    value: {
      ready: Promise.resolve({
        pushManager: { getSubscription: vi.fn().mockResolvedValue(subscription) },
      }),
    },
  });
}

function createWrapper() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
  const wrapper = ({ children }: { children: ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children);
  return { wrapper, invalidateSpy };
}

describe('usePushDevices — 해지 ↔ 옵트인 동기화 (TASK-FE-088)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockList.mockResolvedValue([]);
    mockDelete.mockResolvedValue(undefined);
  });

  afterEach(() => {
    // @ts-expect-error test cleanup of the injected property
    delete navigator.serviceWorker;
  });

  it('현재 브라우저 기기 해지 시 브라우저 구독을 해제하고 push-subscription 쿼리를 무효화한다', async () => {
    const subscription = fakeSubscription(CURRENT);
    setServiceWorker(subscription);

    const { wrapper, invalidateSpy } = createWrapper();
    const { result } = renderHook(() => usePushDevices(), { wrapper });

    await act(async () => {
      await result.current.removeDevice(CURRENT);
    });

    expect(mockDelete).toHaveBeenCalledWith(CURRENT);
    // Browser subscription for THIS device is torn down so the opt-in button re-syncs.
    expect(subscription.unsubscribe).toHaveBeenCalled();
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: notificationKeys.pushSubscription() });
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: notificationKeys.pushDevices() });
  });

  it('다른 기기 해지 시 현재 브라우저 구독은 해제하지 않는다', async () => {
    const subscription = fakeSubscription(CURRENT);
    setServiceWorker(subscription);

    const { wrapper, invalidateSpy } = createWrapper();
    const { result } = renderHook(() => usePushDevices(), { wrapper });

    await act(async () => {
      await result.current.removeDevice(OTHER);
    });

    expect(mockDelete).toHaveBeenCalledWith(OTHER);
    // The current browser stays subscribed — only the remote device's server record is dropped.
    expect(subscription.unsubscribe).not.toHaveBeenCalled();
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: notificationKeys.pushDevices() });
  });
});
