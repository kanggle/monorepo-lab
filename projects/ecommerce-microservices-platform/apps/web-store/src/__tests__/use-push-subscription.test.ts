import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';

vi.mock('@/features/notification/lib/web-push', () => ({
  isPushSupported: vi.fn(() => true),
  registerServiceWorker: vi.fn(),
  urlBase64ToUint8Array: vi.fn(() => new Uint8Array([1, 2, 3])),
}));

vi.mock('@/features/notification/api/push-subscription-api', () => ({
  getVapidPublicKey: vi.fn(),
  registerPushSubscription: vi.fn(),
  deletePushSubscription: vi.fn(),
}));

import { usePushSubscription } from '@/features/notification/model/use-push-subscription';
import { registerServiceWorker } from '@/features/notification/lib/web-push';
import {
  getVapidPublicKey,
  registerPushSubscription,
  deletePushSubscription,
} from '@/features/notification/api/push-subscription-api';

const mockRegisterSW = vi.mocked(registerServiceWorker);
const mockGetKey = vi.mocked(getVapidPublicKey);
const mockRegister = vi.mocked(registerPushSubscription);
const mockDelete = vi.mocked(deletePushSubscription);

function fakeSubscription(endpoint = 'https://push/ep') {
  return {
    endpoint,
    expirationTime: null,
    toJSON: () => ({ endpoint, keys: { p256dh: 'p', auth: 'a' } }),
    unsubscribe: vi.fn().mockResolvedValue(true),
  };
}

function setServiceWorker(existingSubscription: unknown) {
  Object.defineProperty(navigator, 'serviceWorker', {
    configurable: true,
    value: {
      ready: Promise.resolve({
        pushManager: { getSubscription: vi.fn().mockResolvedValue(existingSubscription) },
      }),
    },
  });
}

function setNotification(permissionResult: string) {
  vi.stubGlobal('Notification', {
    permission: 'default',
    requestPermission: vi.fn().mockResolvedValue(permissionResult),
  });
}

describe('usePushSubscription', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setNotification('granted');
    setServiceWorker(null);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    // @ts-expect-error test cleanup of the injected property
    delete navigator.serviceWorker;
  });

  it('권한 허용 시 구독을 생성하고 백엔드에 등록한다', async () => {
    mockGetKey.mockResolvedValue('KEY');
    mockRegister.mockResolvedValue('sub-1');
    const subscription = fakeSubscription();
    mockRegisterSW.mockResolvedValue({
      pushManager: { subscribe: vi.fn().mockResolvedValue(subscription) },
    } as unknown as ServiceWorkerRegistration);

    const { result } = renderHook(() => usePushSubscription());

    await act(async () => {
      await result.current.subscribe();
    });

    expect(mockRegister).toHaveBeenCalledWith({
      endpoint: 'https://push/ep',
      expirationTime: null,
      keys: { p256dh: 'p', auth: 'a' },
    });
    expect(result.current.subscribed).toBe(true);
    expect(result.current.permission).toBe('granted');
  });

  it('권한 거부 시 백엔드 등록을 하지 않는다', async () => {
    setNotification('denied');

    const { result } = renderHook(() => usePushSubscription());

    await act(async () => {
      await result.current.subscribe();
    });

    expect(mockRegister).not.toHaveBeenCalled();
    expect(result.current.permission).toBe('denied');
    expect(result.current.subscribed).toBe(false);
  });

  it('해지 시 백엔드 삭제 후 브라우저 구독을 해제한다', async () => {
    const subscription = fakeSubscription();
    setServiceWorker(subscription);
    mockDelete.mockResolvedValue(undefined);

    const { result } = renderHook(() => usePushSubscription());

    // mount reflects the existing subscription
    await waitFor(() => expect(result.current.subscribed).toBe(true));

    await act(async () => {
      await result.current.unsubscribe();
    });

    expect(mockDelete).toHaveBeenCalledWith('https://push/ep');
    expect(subscription.unsubscribe).toHaveBeenCalled();
    expect(result.current.subscribed).toBe(false);
  });
});
