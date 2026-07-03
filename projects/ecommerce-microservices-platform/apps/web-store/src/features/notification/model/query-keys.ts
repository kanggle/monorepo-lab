export const notificationKeys = {
  all: ['notifications'] as const,
  lists: () => [...notificationKeys.all, 'list'] as const,
  list: (params: Record<string, unknown>) => [...notificationKeys.lists(), params] as const,
  details: () => [...notificationKeys.all, 'detail'] as const,
  detail: (id: string) => [...notificationKeys.details(), id] as const,
  preferences: () => [...notificationKeys.all, 'preferences'] as const,
  pushDevices: () => [...notificationKeys.all, 'push-devices'] as const,
  // This browser's own Web Push subscription state (shared so the opt-in button and
  // the device-list "해지" action stay in sync across components).
  pushSubscription: () => [...notificationKeys.all, 'push-subscription'] as const,
};
