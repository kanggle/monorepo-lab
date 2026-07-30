'use client';

import { type RefObject } from 'react';

/**
 * Presentational body of {@link ConfirmActionDialog} (TASK-PC-FE-210 split,
 * trimmed in TASK-PC-FE-262). Rendered as the shared `shared/ui/ConfirmDialog`
 * primitive's `children`, INSIDE its `role="dialog"` frame — the reason /
 * typed state, the open-reset effect and both refs stay in the container, and
 * the shell (title / description / error banner / footer / focus trap / Escape)
 * is now the shared primitive's job.
 *
 * This file therefore renders ONLY the two domain fields: the required reason
 * textarea (attaching the container's `reasonRef`) and the optional
 * typed-confirmation input (gdpr-delete). Every `data-testid` / aria / class /
 * copy is verbatim.
 */
export interface ConfirmActionDialogBodyProps {
  requireTypedConfirmation?: string;
  reasonId: string;
  typedId: string;
  reasonRef: RefObject<HTMLTextAreaElement | null>;
  reason: string;
  onReasonChange: (value: string) => void;
  typed: string;
  onTypedChange: (value: string) => void;
  reasonOk: boolean;
}

export function ConfirmActionDialogBody({
  requireTypedConfirmation,
  reasonId,
  typedId,
  reasonRef,
  reason,
  onReasonChange,
  typed,
  onTypedChange,
  reasonOk,
}: ConfirmActionDialogBodyProps) {
  return (
    <>
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
    </>
  );
}
