'use client';

import type { RefObject } from 'react';
import { Button } from '@/shared/ui/Button';

/**
 * Presentational body of `OperatorProfileEditDialog` (TASK-PC-FE-209 split).
 * Renders inside the container's `role="dialog"` frame — the open-reset +
 * auto-focus effect, the Escape / Tab focus-trap handler, and `dialogRef` stay
 * in the container so the focus trap (`dialogRef.current.querySelectorAll`) and
 * ref-based auto-focus are unchanged; `valueRef` is forwarded here and attached
 * to the finance-account input so first-input auto-focus still lands. All
 * state / validation / submit live in the container and arrive via props.
 */
export interface OperatorProfileEditDialogBodyProps {
  titleId: string;
  descId: string;
  valueId: string;
  reasonId: string;
  clearId: string;
  valueRef: RefObject<HTMLInputElement | null>;
  operatorIdLabel: string;
  initialDefaultAccountId?: string | null;
  value: string;
  setValue: (v: string) => void;
  cleared: boolean;
  setCleared: (v: boolean) => void;
  reason: string;
  setReason: (v: string) => void;
  valueClientError: string | null;
  reasonOk: boolean;
  errorMessage?: string | null;
  pending: boolean;
  canConfirm: boolean;
  onCancel: () => void;
  submit: () => void;
}

export function OperatorProfileEditDialogBody({
  titleId,
  descId,
  valueId,
  reasonId,
  clearId,
  valueRef,
  operatorIdLabel,
  initialDefaultAccountId,
  value,
  setValue,
  cleared,
  setCleared,
  reason,
  setReason,
  valueClientError,
  reasonOk,
  errorMessage,
  pending,
  canConfirm,
  onCancel,
  submit,
}: OperatorProfileEditDialogBodyProps) {
  return (
    <>
      <h2 id={titleId} className="text-lg font-semibold text-foreground">
        프로파일 편집 — {operatorIdLabel}
      </h2>
      <p
        id={descId}
        data-testid="operator-profile-edit-hint"
        className="mt-2 text-sm text-muted-foreground"
      >
        현재 값: {initialDefaultAccountId ? initialDefaultAccountId : '미설정'}.
        입력값이 그대로 저장됩니다. 기본 계정을 비우려면 아래 Clear 토글을
        사용하세요. 이 작업은 감사 사유와 함께 기록됩니다.
      </p>

      <div className="mt-4">
        <label
          htmlFor={valueId}
          className="block text-sm font-medium text-foreground"
        >
          기본 finance 계정 ID
        </label>
        <input
          id={valueId}
          ref={valueRef}
          type="text"
          value={value}
          onChange={(e) => setValue(e.target.value)}
          disabled={cleared}
          placeholder="예: 01928c4a-7e9f-7c00-9a40-d2b1f5e8a000"
          maxLength={36}
          autoComplete="off"
          spellCheck={false}
          aria-invalid={valueClientError !== null}
          data-testid="operator-profile-edit-value"
          className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary disabled:opacity-50"
        />
        <p className="mt-1 text-xs text-muted-foreground">
          UUID 형식 권장 (최대 36자, 공백·제어문자 금지). GAP은 이 값을
          finance에 verify하지 않습니다 (opaque).
        </p>
        {valueClientError && (
          <p
            className="mt-1 text-xs text-destructive"
            data-testid="operator-profile-edit-value-error"
          >
            {valueClientError}
          </p>
        )}
      </div>

      <div className="mt-3 flex items-center gap-2">
        <input
          id={clearId}
          type="checkbox"
          checked={cleared}
          onChange={(e) => setCleared(e.target.checked)}
          data-testid="operator-profile-edit-clear"
        />
        <label
          htmlFor={clearId}
          className="text-sm text-foreground"
        >
          기본 계정 제거 (저장 시 <code>null</code> 전송)
        </label>
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
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          required
          aria-required="true"
          rows={3}
          data-testid="operator-profile-edit-reason"
          className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          placeholder="이 운영 작업의 사유를 입력하세요 (감사 기록에 남습니다)"
        />
        {!reasonOk && (
          <p
            className="mt-1 text-xs text-muted-foreground"
            data-testid="operator-profile-edit-reason-required"
          >
            사유를 입력해야 작업을 진행할 수 있습니다.
          </p>
        )}
      </div>

      {errorMessage && (
        <p
          role="alert"
          data-testid="operator-profile-edit-error"
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
          data-testid="operator-profile-edit-cancel"
        >
          취소
        </Button>
        <Button
          variant="primary"
          onClick={submit}
          disabled={!canConfirm}
          data-testid="operator-profile-edit-save"
        >
          {pending ? '처리 중…' : '저장'}
        </Button>
      </div>
    </>
  );
}
