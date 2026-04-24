import type { NotificationChannel } from '@repo/types';

export const CHANNEL_LABELS: Record<NotificationChannel, string> = {
  EMAIL: '이메일',
  SMS: 'SMS',
  PUSH: '푸시',
};
