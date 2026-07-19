'use client';

import { useId, useState } from 'react';
import { Button } from '@/shared/ui/Button';
import type {
  Group,
  CreateGroupInput,
  UpdateGroupInput,
} from '../api/types';

/**
 * Group create / edit form (TASK-PC-FE-250). Collects the draft ONLY — it does
 * NOT call the producer; the parent hands the validated draft to the
 * reason-capture + confirm dialog ({@link GroupReasonDialog}), mirroring the
 * `TenantForm` → `TenantConfirmDialog` two-step pattern (draft here, audit
 * reason there — a group mutation never fires one-click).
 *
 * `mode='create'`: tenantId (`!= '*'` — no platform-global group) + name
 * (1~120) + description (≤255).
 * `mode='edit'`: name + description only (both optional, ≥1 required) —
 * `tenantId` is read-only (the producer PATCH accepts neither the tenant nor a
 * re-owner; the group stays within its owning tenant's `TenantScopeGuard`).
 */
export interface GroupFormProps {
  mode: 'create' | 'edit';
  /** Required when `mode='edit'` — prefills the form. */
  group?: Group;
  onSubmitCreateDraft?: (draft: CreateGroupInput) => void;
  onSubmitUpdateDraft?: (draft: UpdateGroupInput) => void;
  serverError?: string | null;
  pending?: boolean;
}

export function GroupForm({
  mode,
  group,
  onSubmitCreateDraft,
  onSubmitUpdateDraft,
  serverError,
  pending = false,
}: GroupFormProps) {
  const tenantIdFieldId = useId();
  const nameFieldId = useId();
  const descriptionFieldId = useId();

  const [tenantId, setTenantId] = useState('');
  const [name, setName] = useState(group?.name ?? '');
  const [description, setDescription] = useState(group?.description ?? '');
  const [touched, setTouched] = useState(false);

  const trimmedName = name.trim();
  const nameOk = trimmedName.length > 0 && trimmedName.length <= 120;
  const tenantIdOk = tenantId.trim().length > 0 && tenantId.trim() !== '*';
  const descriptionOk = description.length <= 255;

  // edit: at least one of name/description must change from the current group.
  const nameChanged = mode === 'edit' && trimmedName !== (group?.name ?? '');
  const descriptionChanged =
    mode === 'edit' && description.trim() !== (group?.description ?? '');
  const editHasChange = nameChanged || descriptionChanged;

  const canSubmit =
    !pending &&
    descriptionOk &&
    (mode === 'create'
      ? tenantIdOk && nameOk
      : (nameChanged ? nameOk : true) && editHasChange);

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setTouched(true);
    if (!canSubmit) return;
    if (mode === 'create' && onSubmitCreateDraft) {
      onSubmitCreateDraft({
        tenantId: tenantId.trim(),
        name: trimmedName,
        description: description.trim() === '' ? undefined : description.trim(),
      });
    } else if (mode === 'edit' && onSubmitUpdateDraft) {
      const draft: UpdateGroupInput = {};
      if (nameChanged) draft.name = trimmedName;
      if (descriptionChanged) draft.description = description.trim();
      onSubmitUpdateDraft(draft);
    }
  }

  return (
    <form
      onSubmit={handleSubmit}
      className="mb-8 grid gap-4 rounded-md border border-border bg-background p-4 sm:grid-cols-2"
      aria-label={mode === 'create' ? '운영자 그룹 등록' : '운영자 그룹 수정'}
      data-testid={mode === 'create' ? 'group-create-form' : 'group-edit-form'}
    >
      <h2 className="sm:col-span-2 text-lg font-semibold text-foreground">
        {mode === 'create' ? '운영자 그룹 등록' : '이름 · 설명 변경'}
      </h2>

      {mode === 'create' ? (
        <div>
          <label
            htmlFor={tenantIdFieldId}
            className="block text-sm font-medium text-foreground"
          >
            테넌트 ID <span aria-hidden="true">*</span>
          </label>
          <input
            id={tenantIdFieldId}
            type="text"
            value={tenantId}
            onChange={(e) => setTenantId(e.target.value)}
            required
            aria-required="true"
            aria-invalid={touched && !tenantIdOk}
            data-testid="group-form-tenant-id"
            className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
            placeholder="acme-corp"
          />
          <p className="mt-1 text-xs text-muted-foreground">
            그룹이 소속될 테넌트. 플랫폼 전역(`*`) 그룹은 만들 수 없으며 본인
            스코프 이내여야 합니다.
          </p>
          {touched && !tenantIdOk && (
            <p
              className="mt-1 text-xs text-destructive"
              data-testid="group-form-tenant-id-error"
            >
              테넌트 ID를 입력하세요 (플랫폼 전역 `*` 불가).
            </p>
          )}
        </div>
      ) : (
        group && (
          <div>
            <span className="block text-sm font-medium text-foreground">
              테넌트 ID
            </span>
            <p
              className="mt-1 rounded-md border border-border bg-muted px-3 py-2 text-sm text-muted-foreground"
              data-testid="group-form-tenant-id-readonly"
            >
              {group.tenantId}
            </p>
          </div>
        )
      )}

      <div>
        <label
          htmlFor={nameFieldId}
          className="block text-sm font-medium text-foreground"
        >
          이름 {mode === 'create' && <span aria-hidden="true">*</span>}
        </label>
        <input
          id={nameFieldId}
          type="text"
          value={name}
          onChange={(e) => setName(e.target.value)}
          maxLength={120}
          data-testid="group-form-name"
          className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          placeholder="물류 지원팀"
        />
        {touched && mode === 'create' && !nameOk && (
          <p
            className="mt-1 text-xs text-destructive"
            data-testid="group-form-name-error"
          >
            1~120자로 입력하세요.
          </p>
        )}
      </div>

      <div className="sm:col-span-2">
        <label
          htmlFor={descriptionFieldId}
          className="block text-sm font-medium text-foreground"
        >
          설명
        </label>
        <textarea
          id={descriptionFieldId}
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          maxLength={255}
          rows={2}
          data-testid="group-form-description"
          className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          placeholder="선택 설명 (255자 이하)"
        />
      </div>

      {serverError && (
        <p
          role="alert"
          data-testid="group-form-server-error"
          className="sm:col-span-2 rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive"
        >
          {serverError}
        </p>
      )}

      <div className="sm:col-span-2">
        <Button
          type="submit"
          disabled={!canSubmit}
          data-testid={mode === 'create' ? 'group-create-submit' : 'group-edit-submit'}
        >
          {pending
            ? '처리 중…'
            : mode === 'create'
              ? '그룹 등록 (확인 필요)'
              : '수정 (확인 필요)'}
        </Button>
      </div>
    </form>
  );
}
