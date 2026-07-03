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
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: notificationKeys.pushDevices() }),
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
