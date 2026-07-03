'use client';

import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { listPushDevices, deletePushSubscription } from '../api/push-subscription-api';
import { isPushSupported } from '../lib/web-push';
import { notificationKeys } from './query-keys';

/**
 * The user's registered push devices (TASK-FE-085) plus this browser's own
 * subscription endpoint, so the list can mark the current device. Removing a
 * device reuses the existing DELETE endpoint and refreshes the list.
 */
export function usePushDevices() {
  const queryClient = useQueryClient();
  const [currentEndpoint, setCurrentEndpoint] = useState<string | null>(null);

  const query = useQuery({
    queryKey: notificationKeys.pushDevices(),
    queryFn: listPushDevices,
  });

  useEffect(() => {
    if (!isPushSupported()) return;
    navigator.serviceWorker.ready
      .then((registration) => registration.pushManager.getSubscription())
      .then((subscription) => setCurrentEndpoint(subscription?.endpoint ?? null))
      .catch(() => setCurrentEndpoint(null));
  }, []);

  const removal = useMutation({
    mutationFn: (endpoint: string) => deletePushSubscription(endpoint),
    onSuccess: async (_data, endpoint) => {
      // If the removed device IS this browser, also tear down its Web Push
      // subscription and invalidate the shared subscription state so the opt-in
      // button (usePushSubscription) flips back to "받기" without a reload.
      // Re-read the live subscription (not the possibly-stale currentEndpoint)
      // so a just-subscribed device is matched correctly.
      if (isPushSupported()) {
        try {
          const registration = await navigator.serviceWorker.ready;
          const subscription = await registration.pushManager.getSubscription();
          if (subscription && subscription.endpoint === endpoint) {
            await subscription.unsubscribe();
            setCurrentEndpoint(null);
          }
        } catch {
          /* browser unsubscribe failed — server record is already gone, ignore */
        }
      }
      queryClient.invalidateQueries({ queryKey: notificationKeys.pushDevices() });
      queryClient.invalidateQueries({ queryKey: notificationKeys.pushSubscription() });
    },
  });

  return {
    devices: query.data ?? [],
    currentEndpoint,
    isLoading: query.isLoading,
    isError: query.isError,
    refetch: query.refetch,
    removeDevice: removal.mutateAsync,
    removingEndpoint: removal.isPending ? removal.variables ?? null : null,
  };
}
