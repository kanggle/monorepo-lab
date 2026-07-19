import { createListQueryKeys } from '@/shared/lib/query-keys';

const base = createListQueryKeys('notifications');

export const notificationKeys = {
  ...base,
  details: () => [...base.all, 'detail'] as const,
  detail: (id: string) => [...notificationKeys.details(), id] as const,
  preferences: () => [...base.all, 'preferences'] as const,
  pushDevices: () => [...base.all, 'push-devices'] as const,
  // This browser's own Web Push subscription state (shared so the opt-in button and
  // the device-list "해지" action stay in sync across components).
  pushSubscription: () => [...base.all, 'push-subscription'] as const,
};
