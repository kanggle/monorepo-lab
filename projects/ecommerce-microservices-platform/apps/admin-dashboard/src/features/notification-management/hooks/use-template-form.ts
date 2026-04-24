import { useState } from 'react';
import { useRouter } from 'next/navigation';
import type {
  NotificationTemplateType,
  NotificationChannel,
  CreateNotificationTemplateRequest,
  UpdateNotificationTemplateRequest,
} from '@repo/types';
import { useSubmitAction } from '@/shared/hooks/use-async-action';
import { useCreateTemplate } from './use-create-template';
import { useUpdateTemplate } from './use-update-template';
import type { TemplateEditData } from '../types';

export function useTemplateForm(template?: TemplateEditData) {
  const isEdit = !!template;
  const router = useRouter();
  const createTemplate = useCreateTemplate();
  const updateTemplate = useUpdateTemplate();

  const [type, setType] = useState<NotificationTemplateType>(
    template?.type ?? 'ORDER_PLACED',
  );
  const [channel, setChannel] = useState<NotificationChannel>(
    template?.channel ?? 'EMAIL',
  );
  const [subject, setSubject] = useState(template?.subject ?? '');
  const [body, setBody] = useState(template?.body ?? '');
  const { error, isSubmitting, runSubmit } = useSubmitAction();
  const isValid = subject.trim().length > 0 && body.trim().length > 0;

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!isValid || isSubmitting) return;

    await runSubmit(async () => {
      if (isEdit) {
        const data: UpdateNotificationTemplateRequest = {
          subject: subject.trim(),
          body: body.trim(),
        };
        await updateTemplate.mutateAsync({
          templateId: template.templateId,
          data,
        });
        router.push('/notifications/templates');
      } else {
        const data: CreateNotificationTemplateRequest = {
          type,
          channel,
          subject: subject.trim(),
          body: body.trim(),
        };
        await createTemplate.mutateAsync(data);
        router.push('/notifications/templates');
      }
    }, '저장에 실패했습니다.');
  }

  return {
    type,
    setType,
    channel,
    setChannel,
    subject,
    setSubject,
    body,
    setBody,
    error,
    isSubmitting,
    isEdit,
    isValid,
    handleSubmit,
  };
}
