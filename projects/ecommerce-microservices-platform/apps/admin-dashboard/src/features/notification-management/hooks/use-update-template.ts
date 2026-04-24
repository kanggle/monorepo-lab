import { updateTemplate } from '../api/notification-api';
import { templateKeys } from './query-keys';
import { useInvalidatingMutation } from '@/shared/hooks';
import type { UpdateNotificationTemplateRequest } from '@repo/types';

export function useUpdateTemplate() {
  return useInvalidatingMutation({
    mutationFn: ({ templateId, data }: { templateId: string; data: UpdateNotificationTemplateRequest }) =>
      updateTemplate(templateId, data),
    invalidate: [templateKeys.all],
    errorMessage: '알림 템플릿 수정에 실패했습니다.',
  });
}
