'use client';

import { useCallback, useEffect, useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import {
  isPushSupported,
  registerServiceWorker,
  urlBase64ToUint8Array,
} from '../lib/web-push';
import {
  getVapidPublicKey,
  registerPushSubscription,
  deletePushSubscription,
} from '../api/push-subscription-api';
import { notificationKeys } from './query-keys';

export type PushPermission = 'default' | 'granted' | 'denied' | 'unsupported';

export interface UsePushSubscriptionResult {
  supported: boolean;
  permission: PushPermission;
  subscribed: boolean;
  isBusy: boolean;
  error: string | null;
  subscribe: () => Promise<void>;
  unsubscribe: () => Promise<void>;
}

/**
 * Manages this browser's Web Push subscription (TASK-FE-083): permission state,
 * subscribe (request permission → SW register → VAPID subscribe → register with
 * backend) and unsubscribe (delete on backend → browser unsubscribe). All browser
 * API access is feature-guarded, so it degrades to an "unsupported" state safely.
 */
export function usePushSubscription(): UsePushSubscriptionResult {
  const queryClient = useQueryClient();
  const [supported, setSupported] = useState(false);
  const [permission, setPermission] = useState<PushPermission>('unsupported');
  const [isBusy, setIsBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!isPushSupported()) {
      setSupported(false);
      setPermission('unsupported');
      return;
    }
    setSupported(true);
    setPermission(Notification.permission as PushPermission);
  }, []);

  // `subscribed` is a SHARED query (not local state) so a "해지" performed in the
  // device list (which invalidates this key after tearing down the browser
  // subscription) flips the opt-in button back to "받기" without a reload.
  const { data: subscribed = false } = useQuery({
    queryKey: notificationKeys.pushSubscription(),
    queryFn: async () => {
      const registration = await navigator.serviceWorker.ready;
      const subscription = await registration.pushManager.getSubscription();
      return subscription !== null;
    },
    enabled: supported,
  });

  const subscribe = useCallback(async () => {
    setError(null);
    if (!isPushSupported()) return;
    setIsBusy(true);
    try {
      const result = await Notification.requestPermission();
      setPermission(result as PushPermission);
      if (result !== 'granted') return;

      const registration = await registerServiceWorker();
      const publicKey = await getVapidPublicKey();
      const subscription = await registration.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: urlBase64ToUint8Array(publicKey),
      });

      const json = subscription.toJSON();
      await registerPushSubscription({
        endpoint: subscription.endpoint,
        expirationTime: subscription.expirationTime ?? null,
        keys: {
          p256dh: json.keys?.p256dh ?? '',
          auth: json.keys?.auth ?? '',
        },
      });
      queryClient.setQueryData(notificationKeys.pushSubscription(), true);
      // Reflect the newly-registered device in the "push devices" list (TASK-FE-085-fix-001).
      queryClient.invalidateQueries({ queryKey: notificationKeys.pushDevices() });
    } catch {
      setError('푸시 알림 구독에 실패했습니다.');
    } finally {
      setIsBusy(false);
    }
  }, [queryClient]);

  const unsubscribe = useCallback(async () => {
    setError(null);
    if (!isPushSupported()) return;
    setIsBusy(true);
    try {
      const registration = await navigator.serviceWorker.ready;
      const subscription = await registration.pushManager.getSubscription();
      if (subscription) {
        await deletePushSubscription(subscription.endpoint);
        await subscription.unsubscribe();
      }
      queryClient.setQueryData(notificationKeys.pushSubscription(), false);
      // Drop the removed device from the "push devices" list (TASK-FE-085-fix-001).
      queryClient.invalidateQueries({ queryKey: notificationKeys.pushDevices() });
    } catch {
      setError('푸시 알림 구독 해지에 실패했습니다.');
    } finally {
      setIsBusy(false);
    }
  }, [queryClient]);

  return { supported, permission, subscribed, isBusy, error, subscribe, unsubscribe };
}
