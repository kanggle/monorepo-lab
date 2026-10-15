'use client';

import { useState } from 'react';
import Link from 'next/link';
import { ApiError, messageForCode } from '@/shared/api/errors';
import {
  useTenantsList,
  useUpdateTenant,
  useReinviteFirstAdmin,
} from '../hooks/use-tenants';
import type { Tenant, TenantPage, TenantStatus, TenantType } from '../api/types';
import { TenantsTable } from './TenantsTable';
import { TenantConfirmDialog } from './TenantConfirmDialog';
import { TenantReinviteDialog } from './TenantReinviteDialog';

/** TASK-MONO-773 S6 — the «관리자 대기» row action in progress (one dialog at a time). */
type RowAction = { kind: 'reinvite' | 'cleanup' | 'finish'; tenant: Tenant } | null;

/** The contract's confirm sentence for «정리» (console-integration-contract § 2.6.4). */
const CLEANUP_COPY = '초대를 취소하고 테넌트를 정지합니다 — 되돌릴 수 없습니다';

function mutationErrorCopy(err: unknown, fallback: string): string | null {
  if (err instanceof ApiError) return messageForCode(err.code, err.message);
  return err ? fallback : null;
}

function newIdempotencyKey(): string {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto
    ? crypto.randomUUID()
    : `first-admin-${Date.now()}`;
}

/**
 * IAM tenant-management list surface (TASK-PC-FE-226 — the isolation-boundary
 * CRUD screen; SUPER_ADMIN only). Server-rendered initial page is passed in;
 * client re-query handles the status/tenantType filter + pagination + post-
 * mutation invalidation (mirrors `OperatorsScreen`).
 *
 * TASK-MONO-773 S6 — the create form is no longer here: «조직 만들기» (`/onboarding`) and «테넌트 등록»
 * (this screen's form) became ONE «테넌트 생성» screen `/new-tenant` (console-integration-contract § 2.6.4;
 * the SUPER_ADMIN form lives in `TenantCreateScreen`). This screen keeps the list and links there.
 *
 * TASK-MONO-773 S6 (owner decision 2026-10-16 UTC) — the «관리자 대기» row actions (§ 2.6.4): «다시 초대»
 * (same address → the invitation's `:resend`; new address / `NONE` → first-admin-invitation), «정리»
 * (`PATCH status=SUSPENDED` behind the in-page confirm dialog) and, for an `ACCEPTED` first admin, «활성화
 * 마치기» (`PATCH status=ACTIVE`). Each needs a reason; each answer refreshes the list.
 *
 * This screen is functionally UNRELATED to `features/tenant`
 * (`TenantSwitcher` — the operator's own active-tenant session switcher);
 * this is the tenant-resource CRUD surface reached from 조직 설정 ▸ 테넌트.
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

  const listApiError = list.error instanceof ApiError ? (list.error as ApiError) : null;
  const isListError = list.isError;

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

  const update = useUpdateTenant();
  const reinvite = useReinviteFirstAdmin();
  const [action, setAction] = useState<RowAction>(null);
  const [reinviteKey, setReinviteKey] = useState<string>('');

  function open(kind: NonNullable<RowAction>['kind'], tenant: Tenant) {
    update.reset();
    reinvite.reset();
    if (kind === 'reinvite') setReinviteKey(newIdempotencyKey());
    setAction({ kind, tenant });
  }

  function confirmStatus(status: 'SUSPENDED' | 'ACTIVE', reason: string) {
    if (!action) return;
    update.mutate(
      { tenantId: action.tenant.tenantId, input: { status }, reason },
      { onSuccess: () => setAction(null) },
    );
  }

  return (
    <section aria-labelledby="tenants-heading">
      <h1 id="tenants-heading" className="mb-2 text-2xl font-semibold">
        테넌트 관리
      </h1>
      <p className="mb-6 text-sm text-muted-foreground">
        테넌트(격리 경계) 조회·수정. 세션의 활성 테넌트를 전환하는 화면이
        아닙니다 — 상단의 테넌트 스위처와는 별개입니다. 모든 변경 작업은
        사유를 요구하며 감사 기록에 남습니다. (SUPER_ADMIN 전용)
      </p>

      <p className="mb-6 text-sm">
        새 테넌트는{' '}
        <Link
          href="/new-tenant"
          data-testid="tenants-create-link"
          className="font-medium text-foreground underline underline-offset-2"
        >
          테넌트 생성
        </Link>{' '}
        화면에서 첫 관리자와 함께 만듭니다.
      </p>

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
        onReinvite={(t) => open('reinvite', t)}
        onCleanup={(t) => open('cleanup', t)}
        onFinishActivation={(t) => open('finish', t)}
      />
      {listApiError && (
        <p className="sr-only" data-testid="tenants-list-error-code">
          {listApiError.code}
        </p>
      )}

      {action?.kind === 'reinvite' && (
        <TenantReinviteDialog
          tenant={action.tenant}
          pending={reinvite.isPending}
          error={mutationErrorCopy(reinvite.error, '첫 관리자를 다시 초대하지 못했습니다.')}
          onConfirm={({ email, displayName, reason }) =>
            reinvite.mutate(
              {
                tenantId: action.tenant.tenantId,
                invitationId: action.tenant.firstAdmin?.invitationId ?? null,
                currentEmail: action.tenant.firstAdmin?.email ?? null,
                email,
                displayName,
                reason,
                idempotencyKey: reinviteKey,
              },
              { onSuccess: () => setAction(null) },
            )
          }
          onCancel={() => setAction(null)}
        />
      )}
      {action?.kind === 'cleanup' && (
        <TenantConfirmDialog
          title="«관리자 대기» 테넌트를 정리할까요?"
          description={`"${action.tenant.displayName}" (${action.tenant.tenantId}) — ${CLEANUP_COPY}.`}
          confirmLabel="정리"
          pending={update.isPending}
          error={mutationErrorCopy(update.error, '테넌트를 정리하지 못했습니다.')}
          onConfirm={(reason) => confirmStatus('SUSPENDED', reason)}
          onCancel={() => setAction(null)}
        />
      )}
      {action?.kind === 'finish' && (
        <TenantConfirmDialog
          title="테넌트 활성화를 마칠까요?"
          description={`"${action.tenant.displayName}" (${action.tenant.tenantId}) — 첫 관리자가 초대를 수락했습니다. 멈춘 활성화를 마쳐 테넌트를 활성 상태로 올립니다.`}
          confirmLabel="활성화 마치기"
          pending={update.isPending}
          error={mutationErrorCopy(update.error, '활성화를 마치지 못했습니다.')}
          onConfirm={(reason) => confirmStatus('ACTIVE', reason)}
          onCancel={() => setAction(null)}
        />
      )}
    </section>
  );
}
