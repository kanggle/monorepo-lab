'use client';

import { useId, useState } from 'react';
import { Button } from '@/shared/ui/Button';
import {
  TENANT_TYPES,
  TENANT_STATUSES,
  tenantIdValidationError,
  type Tenant,
  type TenantType,
  type TenantStatus,
  type CreateTenantInput,
  type UpdateTenantInput,
} from '../api/types';

/**
 * Tenant create/edit form (TASK-PC-FE-226). Collects the draft ONLY — it does
 * NOT call the producer; the parent hands the validated draft to the
 * reason-capture + confirm dialog (`TenantConfirmDialog`), mirroring the
 * `CreateOperatorForm` → `OperatorConfirmDialog` two-step pattern (draft here,
 * audit reason there — a tenant mutation never fires one-click).
 *
 * `mode='create'`: tenantId (immutable once set, contract § Tenant ID 규칙) +
 * displayName + tenantType.
 * `mode='edit'`: displayName + status only — `tenantId`/`tenantType` are
 * read-only (the producer PATCH accepts neither field; a re-assigned
 * `tenant_id` would break the audit trail + external tokens per the
 * contract).
 */
export interface TenantFormProps {
  mode: 'create' | 'edit';
  /** Required when `mode='edit'` — the current tenant (prefills the form). */
  tenant?: Tenant;
  onSubmitCreateDraft?: (draft: CreateTenantInput) => void;
  onSubmitUpdateDraft?: (draft: UpdateTenantInput) => void;
  serverError?: string | null;
  pending?: boolean;
}

export function TenantForm({
  mode,
  tenant,
  onSubmitCreateDraft,
  onSubmitUpdateDraft,
  serverError,
  pending = false,
}: TenantFormProps) {
  const tenantIdFieldId = useId();
  const displayNameFieldId = useId();
  const tenantTypeFieldId = useId();
  const statusFieldId = useId();

  const [tenantId, setTenantId] = useState('');
  const [displayName, setDisplayName] = useState(tenant?.displayName ?? '');
  const [tenantType, setTenantType] = useState<TenantType>('B2C_CONSUMER');
  const [status, setStatus] = useState<TenantStatus>(
    (tenant?.status as TenantStatus) ?? 'ACTIVE',
  );
  const [touched, setTouched] = useState(false);

  const tenantIdError = mode === 'create' ? tenantIdValidationError(tenantId) : null;
  const displayNameOk = displayName.trim().length > 0 && displayName.trim().length <= 100;

  const canSubmit =
    !pending &&
    displayNameOk &&
    (mode === 'edit' || (tenantId.length > 0 && tenantIdError === null));

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setTouched(true);
    if (!canSubmit) return;
    if (mode === 'create' && onSubmitCreateDraft) {
      onSubmitCreateDraft({
        tenantId,
        displayName: displayName.trim(),
        tenantType,
      });
    } else if (mode === 'edit' && onSubmitUpdateDraft) {
      onSubmitUpdateDraft({
        displayName: displayName.trim(),
        status,
      });
    }
  }

  return (
    <form
      onSubmit={handleSubmit}
      className="mb-8 grid gap-4 rounded-md border border-border bg-background p-4 sm:grid-cols-2"
      aria-label={mode === 'create' ? '테넌트 등록' : '테넌트 수정'}
      data-testid={mode === 'create' ? 'tenant-create-form' : 'tenant-edit-form'}
    >
      <h2 className="sm:col-span-2 text-lg font-semibold text-foreground">
        {mode === 'create' ? '테넌트 등록' : '테넌트 수정'}
      </h2>

      {mode === 'create' && (
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
            aria-invalid={touched && tenantIdError !== null}
            data-testid="tenant-form-tenant-id"
            className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          />
          <p className="mt-1 text-xs text-muted-foreground">
            소문자로 시작, 소문자·숫자·하이픈만, 2~32자.
          </p>
          {touched && tenantIdError === 'TENANT_ID_FORMAT_INVALID' && (
            <p
              className="mt-1 text-xs text-destructive"
              data-testid="tenant-form-tenant-id-error"
            >
              형식이 올바르지 않습니다.
            </p>
          )}
          {touched && tenantIdError === 'TENANT_ID_RESERVED' && (
            <p
              className="mt-1 text-xs text-destructive"
              data-testid="tenant-form-tenant-id-error"
            >
              예약어는 테넌트 ID로 사용할 수 없습니다.
            </p>
          )}
        </div>
      )}

      {mode === 'edit' && tenant && (
        <div>
          <span className="block text-sm font-medium text-foreground">테넌트 ID</span>
          <p
            className="mt-1 rounded-md border border-border bg-muted px-3 py-2 text-sm text-muted-foreground"
            data-testid="tenant-form-tenant-id-readonly"
          >
            {tenant.tenantId}
          </p>
        </div>
      )}

      <div>
        <label
          htmlFor={displayNameFieldId}
          className="block text-sm font-medium text-foreground"
        >
          명칭 <span aria-hidden="true">*</span>
        </label>
        <input
          id={displayNameFieldId}
          type="text"
          value={displayName}
          onChange={(e) => setDisplayName(e.target.value)}
          required
          aria-required="true"
          maxLength={100}
          data-testid="tenant-form-display-name"
          className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        />
        {touched && !displayNameOk && (
          <p
            className="mt-1 text-xs text-destructive"
            data-testid="tenant-form-display-name-error"
          >
            1~100자로 입력하세요.
          </p>
        )}
      </div>

      {mode === 'create' && (
        <div>
          <label
            htmlFor={tenantTypeFieldId}
            className="block text-sm font-medium text-foreground"
          >
            구분 <span aria-hidden="true">*</span>
          </label>
          <select
            id={tenantTypeFieldId}
            value={tenantType}
            onChange={(e) => setTenantType(e.target.value as TenantType)}
            required
            aria-required="true"
            data-testid="tenant-form-tenant-type"
            className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            {TENANT_TYPES.map((t) => (
              <option key={t} value={t}>
                {t === 'B2C_CONSUMER' ? 'B2C (소비자)' : 'B2B (기업)'}
              </option>
            ))}
          </select>
        </div>
      )}

      {mode === 'edit' && (
        <div>
          <label
            htmlFor={statusFieldId}
            className="block text-sm font-medium text-foreground"
          >
            상태
          </label>
          <select
            id={statusFieldId}
            value={status}
            onChange={(e) => setStatus(e.target.value as TenantStatus)}
            data-testid="tenant-form-status"
            className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
          >
            {TENANT_STATUSES.map((s) => (
              <option key={s} value={s}>
                {s === 'ACTIVE' ? 'ACTIVE (활성)' : 'SUSPENDED (정지)'}
              </option>
            ))}
          </select>
          {status === 'SUSPENDED' && (
            <p
              className="mt-1 text-xs text-destructive"
              data-testid="tenant-form-suspend-warning"
            >
              SUSPENDED로 전환하면 해당 테넌트의 신규 로그인·신규 사용자 등록이
              차단됩니다.
            </p>
          )}
        </div>
      )}

      {serverError && (
        <p
          role="alert"
          data-testid="tenant-form-server-error"
          className="sm:col-span-2 rounded-md border border-destructive/40 bg-destructive/10 px-3 py-2 text-sm text-destructive"
        >
          {serverError}
        </p>
      )}

      <div className="sm:col-span-2">
        <Button
          type="submit"
          disabled={!canSubmit}
          data-testid={
            mode === 'create' ? 'tenant-create-submit' : 'tenant-edit-submit'
          }
        >
          {pending
            ? '처리 중…'
            : mode === 'create'
              ? '테넌트 등록 (확인 필요)'
              : '수정 (확인 필요)'}
        </Button>
      </div>
    </form>
  );
}
