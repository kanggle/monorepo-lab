'use client';

import { useState } from 'react';
import { ApiError, messageForCode } from '@/shared/api/errors';
import { useCreateTenant } from '../hooks/use-tenants';
import type { CreateTenantInput, TenantOrgNodeOption } from '../api/types';
import { TenantForm } from './TenantForm';
import { TenantConfirmDialog } from './TenantConfirmDialog';

/**
 * The SUPER_ADMIN mode of the one «테넌트 생성» screen `/new-tenant` (TASK-MONO-773 S6 —
 * console-integration-contract § 2.6.4 «first admin by invitation»). The create form + reason/confirm dialog
 * that lived on `/tenants` (TASK-PC-FE-226 · TASK-PC-FE-312 · TASK-MONO-773 S4) moved here verbatim: same
 * `TenantForm`, same `useCreateTenant` → `POST /api/tenants` (operator token) with `firstAdmin`.
 *
 * Success (`201`, the tenant is `PENDING` — «관리자 대기» until the first admin accepts): no re-exchange (nothing
 * about the caller changed) → navigate to `/tenants`, where the new row reads «관리자 대기». `navigate` is
 * injectable for tests (defaults to a client-side push through a hard navigation).
 *
 * `idempotencyKey` is generated ONCE per confirmed create (`crypto.randomUUID()`), reused only if that exact
 * confirmed create is retried — unchanged from the `/tenants` form.
 */
export interface TenantCreateScreenProps {
  /** «소속 노드 (선택)» choices, read server-side by the page (TASK-PC-FE-312). `null`/absent = could not be read. */
  orgNodeOptions?: TenantOrgNodeOption[] | null;
  /** Test seam for the post-success navigation. */
  navigate?: (href: string) => void;
}

/** 404 `ORG_NODE_NOT_FOUND` on a create with a node (TASK-BE-625): normally nothing was created, but in the
 *  documented race the tenant exists UNPLACED — the screen cannot tell which, so it says both (admin-api.md
 *  § POST /api/admin/tenants 순서 노트). */
const CREATE_ORG_NODE_NOT_FOUND_COPY =
  '선택한 소속 노드에 둘 수 없습니다 (삭제되었거나 관리 범위 밖). 테넌트가 무소속으로 이미 만들어졌을 수 있으니 목록을 확인하고, 있으면 조직 계층 화면에서 노드에 두세요.';

function defaultNavigate(href: string): void {
  window.location.assign(href);
}

export function TenantCreateScreen({
  orgNodeOptions = null,
  navigate = defaultNavigate,
}: TenantCreateScreenProps) {
  const create = useCreateTenant();
  const [pendingDraft, setPendingDraft] = useState<CreateTenantInput | null>(null);
  const [idempotencyKey, setIdempotencyKey] = useState<string | null>(null);

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

  function confirmCreate(reason: string) {
    if (!pendingDraft || !idempotencyKey) return;
    create.mutate(
      { input: pendingDraft, reason, idempotencyKey },
      {
        onSuccess: () => {
          setPendingDraft(null);
          navigate('/tenants');
        },
      },
    );
  }

  return (
    <section aria-labelledby="tenant-create-heading" data-testid="tenant-create-screen">
      <h2 id="tenant-create-heading" className="mb-2 text-lg font-semibold">
        테넌트 + 첫 관리자
      </h2>
      <p className="mb-4 text-sm text-muted-foreground">
        새 테넌트를 만들고 첫 관리자에게 초대 메일을 보냅니다. 초대가 수락될 때까지 테넌트는 «관리자 대기»
        상태로 로그인 · 가입이 열리지 않습니다. 모든 변경은 사유를 요구하며 감사 기록에 남습니다.
      </p>

      <TenantForm
        mode="create"
        onSubmitCreateDraft={openCreate}
        serverError={createError}
        pending={create.isPending}
        orgNodeOptions={orgNodeOptions}
      />

      {pendingDraft && (
        <TenantConfirmDialog
          title="테넌트를 등록할까요?"
          description={`"${pendingDraft.displayName}" (${pendingDraft.tenantId}) 테넌트를 새로 등록하고 ${pendingDraft.firstAdmin.email} 에게 첫 관리자 초대를 보냅니다. 초대가 수락될 때까지 «관리자 대기»입니다.`}
          confirmLabel="등록"
          pending={create.isPending}
          error={createError}
          onConfirm={confirmCreate}
          onCancel={() => setPendingDraft(null)}
        />
      )}
    </section>
  );
}
