'use client';

import { useId, useState } from 'react';
import { useRouter } from 'next/navigation';
import { ApiError, messageForCode } from '@/shared/api/errors';
import {
  useCreateTemplate,
  useUpdateTemplate,
} from './use-ecommerce-notifications';
import {
  TEMPLATE_TYPE_VALUES,
  NOTIFICATION_CHANNEL_VALUES,
  type NotificationTemplateDetail,
  type CreateTemplateBody,
  type UpdateTemplateBody,
  type TemplateType,
} from '../api/notification-types';

/**
 * Form state + validation + confirm-gated submit for {@link TemplateForm}
 * (TASK-PC-FE-143 — extracted from the former fat container, behavior-preserving).
 *
 * Owns the type/channel/subject/body state, the create/update mutations
 * (producer keeps type+channel immutable after creation, so update only sends
 * subject+body via PUT) and inline error surfacing. The component consumes the
 * returned values/handlers and only renders — no logic change vs the pre-split
 * single file (validation predicate and wire bodies are identical).
 */
export function useTemplateForm(existing?: NotificationTemplateDetail) {
  const router = useRouter();
  const isEdit = existing !== undefined;

  const typeId = useId();
  const channelId = useId();
  const subjectId = useId();
  const bodyId = useId();

  const [type, setType] = useState<string>(
    existing?.type ?? TEMPLATE_TYPE_VALUES[0],
  );
  const [channel, setChannel] = useState<string>(
    existing?.channel ?? NOTIFICATION_CHANNEL_VALUES[0],
  );
  const [subject, setSubject] = useState(existing?.subject ?? '');
  const [body, setBody] = useState(existing?.body ?? '');

  const [confirmOpen, setConfirmOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const create = useCreateTemplate();
  const update = useUpdateTemplate();
  const pending = create.isPending || update.isPending;

  const formValid =
    subject.trim() !== '' &&
    body.trim() !== '' &&
    (isEdit || (TEMPLATE_TYPE_VALUES.includes(type as TemplateType) &&
      NOTIFICATION_CHANNEL_VALUES.includes(channel as 'EMAIL' | 'SMS' | 'PUSH')));

  function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!formValid) return;
    setError(null);
    setConfirmOpen(true);
  }

  function handleError(err: unknown) {
    const code = err instanceof ApiError ? err.code : 'SERVICE_UNAVAILABLE';
    setError(messageForCode(code, '저장하지 못했습니다.'));
  }

  function confirmSubmit() {
    if (isEdit) {
      const updateBody: UpdateTemplateBody = {
        subject: subject.trim(),
        body: body.trim(),
      };
      update.mutate(
        { id: existing!.templateId, body: updateBody },
        {
          onSuccess: () => {
            setConfirmOpen(false);
            router.push('/ecommerce/notifications/templates');
            router.refresh();
          },
          onError: handleError,
        },
      );
      return;
    }

    const createBody: CreateTemplateBody = {
      type: type as TemplateType,
      channel: channel as 'EMAIL' | 'SMS' | 'PUSH',
      subject: subject.trim(),
      body: body.trim(),
    };
    create.mutate(createBody, {
      onSuccess: () => {
        setConfirmOpen(false);
        router.push('/ecommerce/notifications/templates');
        router.refresh();
      },
      onError: handleError,
    });
  }

  function cancelConfirm() {
    setConfirmOpen(false);
    setError(null);
  }

  return {
    router,
    isEdit,
    ids: { typeId, channelId, subjectId, bodyId },
    fields: {
      type,
      setType,
      channel,
      setChannel,
      subject,
      setSubject,
      body,
      setBody,
    },
    confirmOpen,
    error,
    pending,
    formValid,
    onSubmit,
    confirmSubmit,
    cancelConfirm,
  };
}
