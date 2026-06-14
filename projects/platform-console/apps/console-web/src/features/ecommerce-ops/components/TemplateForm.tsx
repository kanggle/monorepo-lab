'use client';

import { useId, useState } from 'react';
import { useRouter } from 'next/navigation';
import { Button } from '@/shared/ui/Button';
import { ApiError, messageForCode } from '@/shared/api/errors';
import {
  useCreateTemplate,
  useUpdateTemplate,
} from '../hooks/use-ecommerce-notifications';
import {
  TEMPLATE_TYPE_VALUES,
  TEMPLATE_TYPE_LABELS,
  NOTIFICATION_CHANNEL_VALUES,
  type NotificationTemplateDetail,
  type CreateTemplateBody,
  type UpdateTemplateBody,
  type TemplateType,
} from '../api/notification-types';
import { ConfirmDialog } from './ConfirmDialog';

/**
 * Create / update notification template form (TASK-PC-FE-089 — ADR-031 Phase 5b).
 * Used by both the `new` and `[id]/edit` pages.
 *
 *   - CREATE (no `existing`): type + channel selects + subject + body required.
 *     On success → `/ecommerce/notifications/templates`.
 *   - UPDATE (`existing` set): type + channel are READ-ONLY (immutable after
 *     creation — producer accepts only subject + body). On success → list.
 *
 * Confirm-gated submit; 409 TEMPLATE_ALREADY_EXISTS surfaced inline.
 * NO `Idempotency-Key` (the producer defines none). Update uses PUT.
 */

export interface TemplateFormProps {
  /** When set, the form is in UPDATE mode for this template. */
  existing?: NotificationTemplateDetail;
}

export function TemplateForm({ existing }: TemplateFormProps) {
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

  const inputCls =
    'mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary';
  const labelCls = 'block text-sm font-medium text-foreground';
  const readonlyInputCls =
    'mt-1 w-full rounded-md border border-border bg-muted px-3 py-2 text-sm text-muted-foreground';

  return (
    <form
      onSubmit={onSubmit}
      className="max-w-2xl space-y-5"
      data-testid="template-form"
    >
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <div>
          <label htmlFor={typeId} className={labelCls}>
            알림 유형{' '}
            {!isEdit && <span className="text-destructive">*</span>}
            {isEdit && (
              <span className="ml-1 text-xs text-muted-foreground">
                (변경 불가)
              </span>
            )}
          </label>
          {isEdit ? (
            <div
              className={readonlyInputCls}
              data-testid="template-form-type-readonly"
              aria-label="알림 유형 (읽기 전용)"
            >
              {TEMPLATE_TYPE_LABELS[existing!.type as TemplateType] ??
                existing!.type}
            </div>
          ) : (
            <select
              id={typeId}
              value={type}
              onChange={(e) => setType(e.target.value)}
              className={inputCls}
              data-testid="template-form-type"
            >
              {TEMPLATE_TYPE_VALUES.map((t) => (
                <option key={t} value={t}>
                  {TEMPLATE_TYPE_LABELS[t]}
                </option>
              ))}
            </select>
          )}
        </div>
        <div>
          <label htmlFor={channelId} className={labelCls}>
            채널{' '}
            {!isEdit && <span className="text-destructive">*</span>}
            {isEdit && (
              <span className="ml-1 text-xs text-muted-foreground">
                (변경 불가)
              </span>
            )}
          </label>
          {isEdit ? (
            <div
              className={readonlyInputCls}
              data-testid="template-form-channel-readonly"
              aria-label="채널 (읽기 전용)"
            >
              {existing!.channel}
            </div>
          ) : (
            <select
              id={channelId}
              value={channel}
              onChange={(e) => setChannel(e.target.value)}
              className={inputCls}
              data-testid="template-form-channel"
            >
              {NOTIFICATION_CHANNEL_VALUES.map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </select>
          )}
        </div>
      </div>

      <div>
        <label htmlFor={subjectId} className={labelCls}>
          제목 <span className="text-destructive">*</span>
        </label>
        <input
          id={subjectId}
          value={subject}
          onChange={(e) => setSubject(e.target.value)}
          className={inputCls}
          data-testid="template-form-subject"
        />
      </div>

      <div>
        <label htmlFor={bodyId} className={labelCls}>
          본문 <span className="text-destructive">*</span>
        </label>
        <textarea
          id={bodyId}
          value={body}
          onChange={(e) => setBody(e.target.value)}
          rows={6}
          className={inputCls}
          data-testid="template-form-body"
        />
      </div>

      {error && !confirmOpen && (
        <p
          role="alert"
          className="text-sm text-destructive"
          data-testid="template-form-error"
        >
          {error}
        </p>
      )}

      <div className="flex gap-3">
        <Button
          type="submit"
          disabled={!formValid || pending}
          data-testid="template-form-submit"
        >
          {isEdit ? '변경 저장' : '템플릿 등록'}
        </Button>
        <Button
          type="button"
          variant="secondary"
          onClick={() => router.back()}
          data-testid="template-form-cancel"
        >
          취소
        </Button>
      </div>

      <ConfirmDialog
        open={confirmOpen}
        title={isEdit ? '템플릿 변경을 저장할까요?' : '템플릿을 등록할까요?'}
        description={
          isEdit
            ? '입력한 내용으로 알림 템플릿을 수정합니다. 유형과 채널은 변경되지 않습니다.'
            : '입력한 내용으로 새 알림 템플릿을 등록합니다.'
        }
        confirmLabel={isEdit ? '저장' : '등록'}
        pending={pending}
        errorMessage={error}
        onConfirm={confirmSubmit}
        onCancel={() => {
          setConfirmOpen(false);
          setError(null);
        }}
      />
    </form>
  );
}
