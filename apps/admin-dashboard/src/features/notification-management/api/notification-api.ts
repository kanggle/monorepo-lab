import { apiClient } from '@/shared/config/api';
import { createAdminNotificationApi } from '@repo/api-client';
import {
  isMock,
  mockGetTemplates,
  mockGetTemplate,
} from '@/shared/lib/mock-data';
import type {
  PaginatedResponse,
  NotificationTemplateSummary,
  NotificationTemplateDetail,
  CreateNotificationTemplateRequest,
  UpdateNotificationTemplateRequest,
  NotificationTemplateResponse,
  NotificationTemplateListParams,
} from '@repo/types';

const adminNotificationApi = createAdminNotificationApi(apiClient);

export async function getTemplate(
  templateId: string,
): Promise<NotificationTemplateDetail> {
  if (isMock()) return mockGetTemplate(templateId);
  return adminNotificationApi.getTemplate(templateId);
}

export async function getTemplates(
  params?: NotificationTemplateListParams,
): Promise<PaginatedResponse<NotificationTemplateSummary>> {
  if (isMock()) return mockGetTemplates(params);
  return adminNotificationApi.getTemplates(params);
}

export async function createTemplate(
  data: CreateNotificationTemplateRequest,
): Promise<NotificationTemplateResponse> {
  return adminNotificationApi.createTemplate(data);
}

export async function updateTemplate(
  templateId: string,
  data: UpdateNotificationTemplateRequest,
): Promise<NotificationTemplateResponse> {
  return adminNotificationApi.updateTemplate(templateId, data);
}
