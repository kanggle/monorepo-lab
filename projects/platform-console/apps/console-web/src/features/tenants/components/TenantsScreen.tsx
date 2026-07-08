'use client';

import { useState } from 'react';
import { ApiError, messageForCode } from '@/shared/api/errors';
import { useTenantsList, useCreateTenant } from '../hooks/use-tenants';
import type {
  TenantPage,
  TenantStatus,
  TenantType,
  CreateTenantInput,
} from '../api/types';
import { TenantForm } from './TenantForm';
import { TenantConfirmDialog } from './TenantConfirmDialog';
import { TenantsTable } from './TenantsTable';

/**
 * IAM tenant-management list surface (TASK-PC-FE-226 — the isolation-boundary
 * CRUD screen; SUPER_ADMIN only). Server-rendered initial page is passed in;
 * client re-query handles the status/tenantType filter + pagination + post-
 * mutation invalidation (mirrors `OperatorsScreen`).
 *
 * The create mutation is reason + confirm-gated via {@link
 * TenantConfirmDialog} — no one-click mutate. `idempotencyKey` is generated
 * ONCE per confirmed create (`crypto.randomUUID()`), reused only if that
 * exact confirmed create is retried.
 *
 * This screen is functionally UNRELATED to `features/tenant`
 * (`TenantSwitcher` — the operator's own active-tenant session switcher);
 * this is the tenant-resource CRUD surface reached from IAM ▸ 테넌트.
 */
export interface TenantsScreenProps {
  initial: TenantPage;
}

export function TenantsScreen({ initial }: TenantsScreenProps) {
  const [statusFilter, setStatusFilter] = useState<'' | TenantStatus>('');
  const [tenantTypeFilter, setTenantTypeFilter] = useState<'' | TenantType>('');
  const [query, setQuery] = useState<{
    status?: TenantStatus;
    tenantType?: TenantType;
    page: number;
    size: number;
  }>({ page: initial.page, size: initial.size });

  const seeded =
    query.page === initial.page &&
    query.status === undefined &&
    query.tenantType === undefined;
  const list = useTenantsList(query, seeded ? initial : undefined);
  const page = list.data;

  const create = useCreateTenant();
  const [pendingDraft, setPendingDraft] = useState<CreateTenantInput | null>(
    null,
  );
  const [idempotencyKey, setIdempotencyKey] = useState<string | null>(null);

  const listApiError = list.error instanceof ApiError ? (list.error as ApiError) : null;
  const isListError = list.isError;

  const createError =
    create.error instanceof ApiError
      ? messageForCode((create.error as ApiError).code, create.error.message)
      : create.error
        ? '테넌트 등록에 실패했습니다.'
        : null;

  function openCreate(draft: CreateTenantInput) {
    create.reset();
    setIdempotencyKey(
      typeof crypto !== 'undefined' && 'randomUUID' in crypto
        ? crypto.randomUUID()
        : `tenant-create-${Date.now()}`,
    );
    setPendingDraft(draft);
  }

  function closeDialog() {
    setPendingDraft(null);
  }

  function confirmCreate(reason: string) {
    if (!pendingDraft || !idempotencyKey) return;
    create.mutate(
      { input: pendingDraft, reason, idempotencyKey },
      { onSuccess: () => setPendingDraft(null) },
    );
  }

  function submitFilter(e: React.FormEvent) {
    e.preventDefault();
    setQuery({
      status: statusFilter === '' ? undefined : statusFilter,
      tenantType: tenantTypeFilter === '' ? undefined : tenantTypeFilter,
      page: 0,
      size: initial.size,
    });
  }

  const rows = page?.items ?? [];

  return (
    <section aria-labelledby="tenants-heading">
      <h1 id="tenants-heading" className="mb-2 text-2xl font-semibold">
        테넌트 관리
      </h1>
      <p className="mb-6 text-sm text-muted-foreground">
        테넌트(격리 경계) 등록·조회·수정. 세션의 활성 테넌트를 전환하는 화면이
        아닙니다 — 상단의 테넌트 스위처와는 별개입니다. 모든 변경 작업은
        사유를 요구하며 감사 기록에 남습니다. (SUPER_ADMIN 전용)
      </p>

      <TenantForm
        mode="create"
        onSubmitCreateDraft={openCreate}
        serverError={createError}
        pending={create.isPending}
      />

      <TenantsTable
        statusFilter={statusFilter}
        onStatusFilterChange={setStatusFilter}
        tenantTypeFilter={tenantTypeFilter}
        onTenantTypeFilterChange={setTenantTypeFilter}
        onSubmitFilter={submitFilter}
        isListError={isListError}
        rows={rows}
        page={page}
        currentPage={query.page}
        onPrevPage={() => setQuery((q) => ({ ...q, page: Math.max(0, q.page - 1) }))}
        onNextPage={() => setQuery((q) => ({ ...q, page: q.page + 1 }))}
      />
      {listApiError && (
        <p className="sr-only" data-testid="tenants-list-error-code">
          {listApiError.code}
        </p>
      )}

      {pendingDraft && (
        <TenantConfirmDialog
          title="테넌트를 등록할까요?"
          description={`"${pendingDraft.displayName}" (${pendingDraft.tenantId}) 테넌트를 새로 등록합니다.`}
          confirmLabel="등록"
          pending={create.isPending}
          error={createError}
          onConfirm={confirmCreate}
          onCancel={closeDialog}
        />
      )}
    </section>
  );
}
