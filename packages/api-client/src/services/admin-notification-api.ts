import type { ApiClient } from '../client';
import type {
  PaginatedResponse,
  NotificationTemplateSummary,
  NotificationTemplateDetail,
  CreateNotificationTemplateRequest,
  UpdateNotificationTemplateRequest,
  NotificationTemplateResponse,
  NotificationTemplateListParams,
} from '@repo/types';

export function createAdminNotificationApi(client: ApiClient) {
  return {
    getTemplates: (params?: NotificationTemplateListParams) =>
      client.get<PaginatedResponse<NotificationTemplateSummary>>(
        '/api/notifications/templates',
        { params },
      ),

    getTemplate: (templateId: string) =>
      client.get<NotificationTemplateDetail>(
        `/api/notifications/templates/${templateId}`,
      ),

    createTemplate: (data: CreateNotificationTemplateRequest) =>
      client.post<NotificationTemplateResponse>(
        '/api/notifications/templates',
        data,
      ),

    updateTemplate: (
      templateId: string,
      data: UpdateNotificationTemplateRequest,
    ) =>
      client.put<NotificationTemplateResponse>(
        `/api/notifications/templates/${templateId}`,
        data,
      ),
  };
}
