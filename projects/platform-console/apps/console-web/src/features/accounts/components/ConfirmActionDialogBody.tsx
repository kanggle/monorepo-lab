'use client';

import { type ReactNode, type RefObject } from 'react';
import { Button } from '@/shared/ui/Button';

/**
 * Presentational body of {@link ConfirmActionDialog} (TASK-PC-FE-210 split).
 * Rendered INSIDE the container's `role="dialog"` frame — the reason / typed
 * state, the open-reset + auto-focus effect, the Escape / focus-trap key
 * handler, and both `dialogRef` / `reasonRef` stay in the container so the
 * `querySelectorAll` focus-trap and the ref auto-focus are byte-identical to
 * the pre-split component. This file only renders the title / description, the
 * reason field (attaching the container's `reasonRef`), the optional
 * typed-confirmation field (gdpr-delete), the inline error, and the footer.
 * Every `data-testid` / aria / class / copy is verbatim.
 */
export interface ConfirmActionDialogBodyProps {
  title: string;
  description: ReactNode;
  destructive: boolean;
  confirmLabel: string;
  requireTypedConfirmation?: string;
  pending: boolean;
  errorMessage?: string | null;
  titleId: string;
  descId: string;
  reasonId: string;
  typedId: string;
  reasonRef: RefObject<HTMLTextAreaElement | null>;
  reason: string;
  onReasonChange: (value: string) => void;
  typed: string;
  onTypedChange: (value: string) => void;
  reasonOk: boolean;
  canConfirm: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

export function ConfirmActionDialogBody({
  title,
  description,
  destructive,
  confirmLabel,
  requireTypedConfirmation,
  pending,
  errorMessage,
  titleId,
  descId,
  reasonId,
  typedId,
  reasonRef,
  reason,
  onReasonChange,
  typed,
  onTypedChange,
  reasonOk,
  canConfirm,
  onConfirm,
  onCancel,
}: ConfirmActionDialogBodyProps) {
  return (
    <>
      <h2
        id={titleId}
        className={
          destructive
            ? 'text-lg font-semibold text-destructive'
            : 'text-lg font-semibold text-foreground'
        }
      >
        {title}
      </h2>
      <div
        id={descId}
        className="mt-2 text-sm text-muted-foreground"
      >
        {description}
      </div>

      <div className="mt-4">
        <label
          htmlFor={reasonId}
          className="block text-sm font-medium text-foreground"
        >
          감사 사유 <span aria-hidden="true">*</span>
          <span className="sr-only">(필수)</span>
        </label>
        <textarea
          id={reasonId}
          ref={reasonRef}
          value={reason}
          onChange={(e) => onReasonChange(e.target.value)}
          required
          aria-required="true"
          rows={3}
          data-testid="confirm-reason"
          className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          placeholder="이 운영 작업의 사유를 입력하세요 (감사 기록에 남습니다)"
        />
        {!reasonOk && (
          <p
            className="mt-1 text-xs text-muted-foreground"
            data-testid="reason-required-hint"
          >
            사유를 입력해야 작업을 진행할 수 있습니다.
          </p>
        )}
      </div>

      {requireTypedConfirmation && (
        <div className="mt-4">
          <label
            htmlFor={typedId}
            className="block text-sm font-medium text-foreground"
          >
            되돌릴 수 없는 작업입니다. 계속하려면{' '}
            <code className="rounded bg-muted px-1">
              {requireTypedConfirmation}
            </code>{' '}
            를 입력하세요.
          </label>
          <input
            id={typedId}
            type="text"
            value={typed}
            onChange={(e) => onTypedChange(e.target.value)}
            data-testid="confirm-typed"
            autoComplete="off"
            className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-destructive"
          />
        </div>
      )}

      {errorMessage && (
        <p
          role="alert"
          data-testid="confirm-error"
          className="mt-4 rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive"
        >
          {errorMessage}
        </p>
      )}

      <div className="mt-6 flex justify-end gap-3">
        <Button
          variant="secondary"
          onClick={onCancel}
          disabled={pending}
          data-testid="confirm-cancel"
        >
          취소
        </Button>
        <Button
          variant={destructive ? 'primary' : 'primary'}
          onClick={onConfirm}
          disabled={!canConfirm}
          data-testid="confirm-submit"
          className={
            destructive
              ? 'bg-destructive text-destructive-foreground hover:opacity-90'
              : undefined
          }
        >
          {pending ? '처리 중…' : confirmLabel}
        </Button>
      </div>
    </>
  );
}
