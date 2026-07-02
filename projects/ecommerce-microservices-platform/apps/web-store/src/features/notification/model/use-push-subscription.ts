'use client';

import { useCallback, useEffect, useState } from 'react';
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
  const [supported, setSupported] = useState(false);
  const [permission, setPermission] = useState<PushPermission>('unsupported');
  const [subscribed, setSubscribed] = useState(false);
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

    // Reflect an already-active subscription for this browser, if any.
    navigator.serviceWorker.ready
      .then((registration) => registration.pushManager.getSubscription())
      .then((subscription) => setSubscribed(subscription !== null))
      .catch(() => {
        /* no active service worker yet — leave subscribed=false */
      });
  }, []);

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
      setSubscribed(true);
    } catch {
      setError('푸시 알림 구독에 실패했습니다.');
    } finally {
      setIsBusy(false);
    }
  }, []);

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
      setSubscribed(false);
    } catch {
      setError('푸시 알림 구독 해지에 실패했습니다.');
    } finally {
      setIsBusy(false);
    }
  }, []);

  return { supported, permission, subscribed, isBusy, error, subscribe, unsubscribe };
}
