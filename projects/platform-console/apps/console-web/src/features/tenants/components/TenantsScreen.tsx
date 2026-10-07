'use client';

import { useState } from 'react';
import { ApiError, messageForCode } from '@/shared/api/errors';
import { useTenantsList, useCreateTenant } from '../hooks/use-tenants';
import type {
  TenantPage,
  TenantStatus,
  TenantType,
  CreateTenantInput,
  TenantOrgNodeOption,
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
 * this is the tenant-resource CRUD surface reached from 조직 설정 ▸ 테넌트.
 */
export interface TenantsScreenProps {
  initial: TenantPage;
  /** «소속 노드 (선택)» choices, read server-side by the page
   *  (TASK-PC-FE-312). `null`/absent = could not be read. */
  orgNodeOptions?: TenantOrgNodeOption[] | null;
}

/** 404 `ORG_NODE_NOT_FOUND` on a create with a node (TASK-BE-625): normally
 *  nothing was created, but in the documented race the tenant exists UNPLACED
 *  — the screen cannot tell which, so it says both (admin-api.md § POST
 *  /api/admin/tenants 순서 노트). */
const CREATE_ORG_NODE_NOT_FOUND_COPY =
  '선택한 소속 노드에 둘 수 없습니다 (삭제되었거나 관리 범위 밖). 테넌트가 무소속으로 이미 만들어졌을 수 있으니 목록을 확인하고, 있으면 조직 계층 화면에서 노드에 두세요.';

export function TenantsScreen({ initial, orgNodeOptions = null }: TenantsScreenProps) {
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
      ? (create.error as ApiError).code === 'ORG_NODE_NOT_FOUND'
        ? CREATE_ORG_NODE_NOT_FOUND_COPY
        : messageForCode((create.error as ApiError).code, create.error.message)
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
        orgNodeOptions={orgNodeOptions}
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
