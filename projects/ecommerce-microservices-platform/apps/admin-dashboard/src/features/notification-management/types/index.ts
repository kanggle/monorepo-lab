import type {
  NotificationTemplateType,
  NotificationChannel,
} from '@repo/types';

export interface TemplateEditData {
  templateId: string;
  type: NotificationTemplateType;
  channel: NotificationChannel;
  subject: string;
  body: string;
}
