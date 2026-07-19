'use client';

import { useState } from 'react';
import { ApiError, messageForCode } from '@/shared/api/errors';
import { useGroupsList, useCreateGroup } from '../hooks/use-operator-groups';
import type { GroupPage, GroupListParams, CreateGroupInput } from '../api/types';
import { GroupForm } from './GroupForm';
import { GroupReasonDialog } from './GroupReasonDialog';
import { GroupsTable } from './GroupsTable';
import { GroupDetail } from './GroupDetail';

/**
 * 운영자 그룹 management screen (TASK-PC-FE-250 / ADR-MONO-046 § 4 step 3) —
 * the master-detail surface consuming BE-520's `/api/admin/groups`. Left/top:
 * the create form + {@link GroupsTable}. Selecting a row renders
 * {@link GroupDetail} (members + grants panels) inline. Mirrors
 * `TenantsScreen` (seeded list + reason+confirm create) and `OrgHierarchyScreen`
 * (master-detail + grantable-roles threaded to the grant picker).
 *
 * The create mutation is reason + confirm-gated ({@link GroupReasonDialog}) — no
 * one-click mutate. `idempotencyKey` is generated ONCE per confirmed create
 * (`crypto.randomUUID()`), reused only if that exact confirmed create is
 * retried. The producer stays the no-escalation authority — `grantableRoles` is
 * a UX pre-filter for the group-grant picker only.
 */
export interface OperatorGroupsScreenProps {
  initial: GroupPage;
  /** Caller's grantable roles (SSR, fail-graceful); `null` ⇒ full known set. */
  grantableRoles: string[] | null;
}

export function OperatorGroupsScreen({
  initial,
  grantableRoles,
}: OperatorGroupsScreenProps) {
  const [tenantFilter, setTenantFilter] = useState('');
  const [query, setQuery] = useState<GroupListParams>({
    page: initial.page,
    size: initial.size,
  });

  const seeded = (query.page ?? 0) === initial.page && !query.tenantId;
  const list = useGroupsList(query, seeded ? initial : undefined);
  const page = list.data;
  const rows = page?.items ?? [];

  const [selectedId, setSelectedId] = useState<string | null>(null);
  const selected = rows.find((g) => g.groupId === selectedId) ?? null;

  const create = useCreateGroup();
  const [pendingDraft, setPendingDraft] = useState<CreateGroupInput | null>(null);
  const [idempotencyKey, setIdempotencyKey] = useState<string | null>(null);

  const listApiError = list.error instanceof ApiError ? (list.error as ApiError) : null;

  const createError =
    create.error instanceof ApiError
      ? messageForCode((create.error as ApiError).code, create.error.message)
      : create.error
        ? '그룹 등록에 실패했습니다.'
        : null;

  function openCreate(draft: CreateGroupInput) {
    create.reset();
    setIdempotencyKey(
      typeof crypto !== 'undefined' && 'randomUUID' in crypto
        ? crypto.randomUUID()
        : `group-create-${Date.now()}`,
    );
    setPendingDraft(draft);
  }

  function confirmCreate(reason: string) {
    if (!pendingDraft || !idempotencyKey) return;
    create.mutate(
      { input: pendingDraft, reason, idempotencyKey },
      {
        onSuccess: (group) => {
          setPendingDraft(null);
          setSelectedId(group.groupId);
        },
      },
    );
  }

  function submitFilter(e: React.FormEvent) {
    e.preventDefault();
    setSelectedId(null);
    setQuery({
      tenantId: tenantFilter.trim() === '' ? undefined : tenantFilter.trim(),
      page: 0,
      size: initial.size,
    });
  }

  return (
    <section aria-labelledby="operator-groups-heading" className="space-y-6">
      <div>
        <h1 id="operator-groups-heading" className="mb-2 text-2xl font-semibold">
          운영자 그룹
        </h1>
        <p className="text-sm text-muted-foreground">
          여러 운영자를 하나의 그룹으로 묶어 역할·테넌트 배정을 한 번에
          부여합니다 (ADR-MONO-046). 그룹 grant 는 각 현재 멤버의 평범한 직접
          권한 행으로 fan-out 되며, 그룹 멤버십 자체는 평가·격리 축이 아닙니다.
          모든 변경은 사유를 요구하며 감사 기록에 남습니다. 본인이 보유하지 않은
          역할·스코프 밖 테넌트는 부여할 수 없습니다 (no-escalation).
        </p>
      </div>

      <GroupForm
        mode="create"
        onSubmitCreateDraft={openCreate}
        serverError={createError}
        pending={create.isPending}
      />

      <div className="grid gap-6 md:grid-cols-[minmax(20rem,28rem)_1fr]">
        <div className="min-w-0 space-y-3">
          <GroupsTable
            tenantFilter={tenantFilter}
            onTenantFilterChange={setTenantFilter}
            onSubmitFilter={submitFilter}
            isListError={list.isError}
            rows={rows}
            page={page}
            currentPage={query.page ?? 0}
            selectedId={selectedId}
            onSelect={setSelectedId}
            onPrevPage={() =>
              setQuery((q) => ({ ...q, page: Math.max(0, (q.page ?? 0) - 1) }))
            }
            onNextPage={() => setQuery((q) => ({ ...q, page: (q.page ?? 0) + 1 }))}
          />
          {listApiError && (
            <p className="sr-only" data-testid="groups-list-error-code">
              {listApiError.code}
            </p>
          )}
        </div>

        <div className="min-w-0">
          {selected ? (
            <GroupDetail
              key={selected.groupId}
              group={selected}
              grantableRoles={grantableRoles}
              onDeleted={() => setSelectedId(null)}
            />
          ) : (
            <p
              className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
              data-testid="groups-detail-empty"
            >
              목록에서 그룹을 선택하면 멤버·grant 상세가 표시됩니다.
            </p>
          )}
        </div>
      </div>

      {pendingDraft && (
        <GroupReasonDialog
          title="운영자 그룹을 등록할까요?"
          description={`"${pendingDraft.name}" 그룹을 테넌트 ${pendingDraft.tenantId} 에 새로 등록합니다.`}
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
