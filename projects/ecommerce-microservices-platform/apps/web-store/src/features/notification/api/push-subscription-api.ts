import { apiClient } from '@/shared/config/api';
import { createNotificationApi } from '@repo/api-client';
import type { RegisterPushSubscriptionRequest } from '@repo/types';

const notificationApi = createNotificationApi(apiClient);

export async function getVapidPublicKey(): Promise<string> {
  const { publicKey } = await notificationApi.getVapidPublicKey();
  return publicKey;
}

export async function registerPushSubscription(
  data: RegisterPushSubscriptionRequest,
): Promise<string> {
  const { subscriptionId } = await notificationApi.registerPushSubscription(data);
  return subscriptionId;
}

export async function deletePushSubscription(endpoint: string): Promise<void> {
  await notificationApi.deletePushSubscription(endpoint);
}
