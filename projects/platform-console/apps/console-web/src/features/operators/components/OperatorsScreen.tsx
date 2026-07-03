'use client';

import { useMemo, useState } from 'react';
import { ApiError, messageForCode } from '@/shared/api/errors';
import {
  useOperatorsList,
  useCreateOperator,
  useEditOperatorRoles,
  useChangeOperatorStatus,
  useSetOperatorProfile,
  useAssignOperator,
  useUnassignOperator,
} from '../hooks/use-operators';
import {
  type OperatorPage,
  type OperatorSummary,
  type OperatorStatus,
  type CreateOperatorInput,
} from '../api/types';
import { CreateOperatorForm } from './CreateOperatorForm';
import { AssignOperatorForm } from './AssignOperatorForm';
import { OperatorConfirmDialog } from './OperatorConfirmDialog';
import { OperatorProfileEditDialog } from './OperatorProfileEditDialog';
import { OrgScopeDialog } from './OrgScopeDialog';
import { OperatorsTable } from './OperatorsTable';
import {
  type PendingAction,
  newIdemKey,
  operatorConfirmTitle,
  operatorConfirmDescription,
  operatorConfirmLabel,
} from './operators-confirm-copy';

/**
 * IAM operators-management surface (TASK-PC-FE-004 — Phase 2 slice 3, the
 * most privilege-sensitive slice). Server-rendered initial page is passed
 * in; client re-query handles status filter / pagination / post-mutation
 * invalidation.
 *
 * EVERY mutating action (create / edit-roles / change-status) is reason-
 * gated + confirm-gated via {@link OperatorConfirmDialog}; privilege-high
 * actions (create, grant SUPER_ADMIN, suspend, remove-all-roles) get
 * explicit ELEVATED confirm copy. change-password is the self form (its
 * own current-password proof + confirm step; the producer requires no
 * audit reason for the self path). 401 → the shared api client forces
 * re-login; 503/timeout → this section degrades only (shell intact);
 * 403 PERMISSION_DENIED / TENANT_SCOPE_DENIED / 409 / 400 / 404 → inline.
 *
 * `idempotencyKey` is generated ONCE per confirmed CREATE
 * (`crypto.randomUUID()`), reused only if that exact confirmed create is
 * retried. edit-roles / change-status carry NO key (per the producer
 * header matrix — § 2.4.3).
 *
 * ── MODULE SPLIT (TASK-PC-FE-105) ── this container owns ALL state, the
 * mutations, and the gating; the list region (filter + table + pagination)
 * is the prop-driven `OperatorsTable` presentational child, and the
 * pending-action model + the `OperatorConfirmDialog` copy builders live in
 * `operators-confirm-copy.tsx`. The badges / create form / confirm / profile /
 * org-scope dialogs were already separate components.
 */

export interface OperatorsScreenProps {
  initial: OperatorPage;
  /** Tenants the operator may assign on create (from the catalog/registry). */
  tenantOptions?: string[];
  /** True ⇒ this operator is platform-scope → may offer `*` on create. */
  isPlatformOperator?: boolean;
  /** TASK-PC-FE-017 — caller's own `operatorId` (when derivable). When set,
   *  the per-row "Profile 편집" button is disabled on the self row (UX gate;
   *  the producer's `400 SELF_PROFILE_UPDATE_FORBIDDEN_VIA_ADMIN_PATH` is
   *  the fail-safe). `null` ⇒ gate inactive — producer is still authoritative. */
  selfOperatorId?: string | null;
  /** TASK-PC-FE-157 — the active tenant slug (from the server render). When
   *  present, the 테넌트 배정 form + the per-row 배정 해제 action target it.
   *  Absent ⇒ the assignment surface is hidden (the page already gates on a
   *  selected tenant, so this is normally set on the render path). */
  activeTenant?: string | null;
  /** feat/iam-grantable-roles-filter — the seed role names THIS operator may
   *  grant (server-provided, `GET /api/admin/operators/grantable-roles`).
   *  Passed straight through to the create-form + edit-roles role
   *  checkboxes as a UX pre-filter. `null` (absent / fetch failed) ⇒ both
   *  selectors fall back to offering the FULL `KNOWN_OPERATOR_ROLES` set —
   *  never an empty list; the producer `403 ROLE_GRANT_FORBIDDEN` remains
   *  the authoritative no-escalation gate either way. */
  grantableRoles?: string[] | null;
}

export function OperatorsScreen({
  initial,
  tenantOptions = [],
  isPlatformOperator = false,
  selfOperatorId = null,
  activeTenant = null,
  grantableRoles = null,
}: OperatorsScreenProps) {
  const [statusFilter, setStatusFilter] = useState<'' | OperatorStatus>('');
  const [query, setQuery] = useState<{
    status?: OperatorStatus;
    page: number;
    size: number;
  }>({ page: initial.page, size: initial.size });

  const seeded =
    query.page === initial.page && query.status === undefined;
  const list = useOperatorsList(query, seeded ? initial : undefined);
  const page = list.data;

  const create = useCreateOperator();
  const editRoles = useEditOperatorRoles();
  const changeStatus = useChangeOperatorStatus();
  const setProfile = useSetOperatorProfile();
  const assign = useAssignOperator();
  const unassign = useUnassignOperator();

  const [pending, setPending] = useState<PendingAction | null>(null);
  /** TASK-PC-FE-017 — track which row's profile-edit dialog is open by
   *  operatorId. TASK-PC-FE-031 — store the id (NOT the snapshot of the
   *  row) so the dialog's `initialDefaultAccountId` prop tracks the live
   *  list-query data. Snapshotting the row at click time captured the
   *  cached `operatorContext.defaultAccountId` from BEFORE the most recent
   *  setProfile mutation's invalidation refetch settled, which is what
   *  surfaced as the operators-admin-profile.spec.ts:89 race (re-opened
   *  dialog input empty). Deriving the row from the current list query
   *  result via useMemo makes the prop reactive — the dialog re-fires its
   *  useEffect on the next render and pre-populates with the fresh value. */
  const [profileEditOperatorId, setProfileEditOperatorId] = useState<
    string | null
  >(null);
  const profileEditFor = useMemo<OperatorSummary | null>(
    () =>
      profileEditOperatorId === null
        ? null
        : (page?.content.find(
            (o: OperatorSummary) => o.operatorId === profileEditOperatorId,
          ) ?? null),
    [page, profileEditOperatorId],
  );
  // TASK-PC-FE-050 — org_scope (데이터-스코프) dialog target, tracked by
  // operatorId (reactive against the live list query, same posture as the
  // profile-edit dialog).
  const [orgScopeOperatorId, setOrgScopeOperatorId] = useState<string | null>(
    null,
  );
  const orgScopeFor = useMemo<OperatorSummary | null>(
    () =>
      orgScopeOperatorId === null
        ? null
        : (page?.content.find(
            (o: OperatorSummary) => o.operatorId === orgScopeOperatorId,
          ) ?? null),
    [page, orgScopeOperatorId],
  );

  const activeMutation = useMemo(() => {
    switch (pending?.kind) {
      case 'create':
        return create;
      case 'edit-roles':
        return editRoles;
      case 'change-status':
        return changeStatus;
      case 'assign':
        return assign;
      case 'unassign':
        return unassign;
      default:
        return null;
    }
  }, [pending?.kind, create, editRoles, changeStatus, assign, unassign]);

  // 403 (permission / tenant-scope) on the LIST read surfaces as ApiError
  // — render the whole section as inline "not permitted", never crash,
  // never a re-login loop.
  const listApiError =
    list.error instanceof ApiError ? (list.error as ApiError) : null;
  const permissionDenied = listApiError?.status === 403;
  const degraded =
    list.isError && (!listApiError || listApiError.status >= 500);

  const dialogError =
    activeMutation?.error instanceof ApiError
      ? messageForCode(
          (activeMutation.error as ApiError).code,
          activeMutation.error.message,
        )
      : activeMutation?.error
        ? '작업을 완료하지 못했습니다. 잠시 후 다시 시도하세요.'
        : null;

  const createError =
    create.error instanceof ApiError
      ? messageForCode(
          (create.error as ApiError).code,
          create.error.message,
        )
      : create.error
        ? '운영자 계정 생성에 실패했습니다.'
        : null;

  const setProfileError =
    setProfile.error instanceof ApiError
      ? messageForCode(
          (setProfile.error as ApiError).code,
          setProfile.error.message,
        )
      : setProfile.error
        ? '프로파일 저장에 실패했습니다.'
        : null;

  function resetMutations() {
    create.reset();
    editRoles.reset();
    changeStatus.reset();
    assign.reset();
    unassign.reset();
  }

  function openCreate(draft: CreateOperatorInput) {
    resetMutations();
    setPending({
      kind: 'create',
      draft,
      idempotencyKey: newIdemKey(),
      // Creating an operator is itself privilege-high (and granting
      // SUPER_ADMIN doubly so) → elevated confirm copy always. The
      // SUPER_ADMIN grant is additionally called out in the description.
      elevated: true,
    });
  }

  function openEditRoles(operator: OperatorSummary) {
    resetMutations();
    setPending({
      kind: 'edit-roles',
      operator,
      // Elevated copy is computed from the FINAL selection at confirm time
      // (grant SUPER_ADMIN / remove-all) — start elevated so the wording
      // is strong by default for this escalation surface.
      elevated: true,
    });
  }

  function openChangeStatus(operator: OperatorSummary) {
    resetMutations();
    const nextStatus: OperatorStatus =
      operator.status === 'SUSPENDED' ? 'ACTIVE' : 'SUSPENDED';
    setPending({
      kind: 'change-status',
      operator,
      nextStatus,
      // Suspending is privilege-high → elevated copy; re-activating is not.
      elevated: nextStatus === 'SUSPENDED',
    });
  }

  function openProfileEdit(operator: OperatorSummary) {
    setProfile.reset();
    setProfileEditOperatorId(operator.operatorId);
  }

  // TASK-PC-FE-157 — assign a (free-text) operator to the ACTIVE tenant. The
  // target may be outside the active-tenant list scope (the point of
  // assigning is to bring an operator INTO this tenant), so it is an
  // operatorId string, not a row. Guarded by the same reason+confirm dialog.
  function openAssign(operatorId: string) {
    if (!activeTenant) return;
    resetMutations();
    setPending({
      kind: 'assign',
      assignOperatorId: operatorId,
      tenantId: activeTenant,
      // Granting tenant access is privilege-sensitive → elevated confirm copy.
      elevated: true,
    });
  }

  // TASK-PC-FE-157 — remove a row operator's assignment to the ACTIVE tenant.
  function openUnassign(operator: OperatorSummary) {
    if (!activeTenant) return;
    resetMutations();
    setPending({
      kind: 'unassign',
      operator,
      tenantId: activeTenant,
      elevated: true,
    });
  }

  function closeDialog() {
    setPending(null);
  }

  function onConfirm(reason: string, roles?: string[]) {
    if (!pending) return;
    if (pending.kind === 'create' && pending.draft && pending.idempotencyKey) {
      create.mutate(
        {
          input: pending.draft,
          reason,
          idempotencyKey: pending.idempotencyKey,
        },
        { onSuccess: () => setPending(null) },
      );
      return;
    }
    if (pending.kind === 'edit-roles' && pending.operator) {
      editRoles.mutate(
        {
          operatorId: pending.operator.operatorId,
          roles: roles ?? [],
          reason,
        },
        { onSuccess: () => setPending(null) },
      );
      return;
    }
    if (
      pending.kind === 'change-status' &&
      pending.operator &&
      pending.nextStatus
    ) {
      changeStatus.mutate(
        {
          operatorId: pending.operator.operatorId,
          status: pending.nextStatus,
          reason,
        },
        { onSuccess: () => setPending(null) },
      );
      return;
    }
    if (
      pending.kind === 'assign' &&
      pending.assignOperatorId &&
      pending.tenantId
    ) {
      assign.mutate(
        {
          operatorId: pending.assignOperatorId,
          tenantId: pending.tenantId,
          reason,
        },
        { onSuccess: () => setPending(null) },
      );
      return;
    }
    if (
      pending.kind === 'unassign' &&
      pending.operator &&
      pending.tenantId
    ) {
      unassign.mutate(
        {
          operatorId: pending.operator.operatorId,
          tenantId: pending.tenantId,
          reason,
        },
        { onSuccess: () => setPending(null) },
      );
    }
  }

  function submitFilter(e: React.FormEvent) {
    e.preventDefault();
    setQuery({
      status: statusFilter === '' ? undefined : statusFilter,
      page: 0,
      size: initial.size,
    });
  }

  const rows = page?.content ?? [];

  return (
    <section aria-labelledby="operators-heading">
      <h1
        id="operators-heading"
        className="mb-2 text-2xl font-semibold"
      >
        운영자 관리
      </h1>
      <p className="mb-6 text-sm text-muted-foreground">
        운영자 계정 생성·역할 변경·상태 변경·내 비밀번호 변경. 모든 변경 작업은
        사유와 확인이 필요하며 감사 기록에 남습니다. (SUPER_ADMIN /
        operator.manage 권한 필요)
      </p>

      {permissionDenied ? (
        <div
          role="status"
          data-testid="operators-permission-denied"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          {listApiError?.code === 'TENANT_SCOPE_DENIED'
            ? messageForCode('TENANT_SCOPE_DENIED')
            : messageForCode('OPERATOR_MANAGE_REQUIRED')}
        </div>
      ) : degraded ? (
        <div
          role="status"
          data-testid="operators-degraded"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          운영자 서비스를 일시적으로 불러올 수 없습니다. 콘솔의 다른 기능은
          계속 사용할 수 있습니다. 잠시 후 다시 시도하세요.
        </div>
      ) : (
        <>
          <CreateOperatorForm
            tenantOptions={tenantOptions}
            isPlatformOperator={isPlatformOperator}
            onSubmitDraft={openCreate}
            serverError={createError}
            pending={create.isPending}
            grantableRoles={grantableRoles}
          />

          {/* TASK-PC-FE-157 — assign an existing operator to the active
              tenant (delegation onboarding). Shown only when the active
              tenant is known (the page already gates on a selected tenant). */}
          {activeTenant && (
            <AssignOperatorForm
              activeTenant={activeTenant}
              onSubmitOperatorId={openAssign}
              pending={assign.isPending}
            />
          )}

          <OperatorsTable
            statusFilter={statusFilter}
            onStatusFilterChange={setStatusFilter}
            onSubmitFilter={submitFilter}
            isListError={list.isError}
            rows={rows}
            page={page}
            currentPage={query.page}
            onPrevPage={() =>
              setQuery((q) => ({ ...q, page: Math.max(0, q.page - 1) }))
            }
            onNextPage={() => setQuery((q) => ({ ...q, page: q.page + 1 }))}
            selfOperatorId={selfOperatorId}
            onEditRoles={openEditRoles}
            onChangeStatus={openChangeStatus}
            onEditProfile={openProfileEdit}
            onOrgScope={(op) => setOrgScopeOperatorId(op.operatorId)}
            onUnassign={activeTenant ? openUnassign : undefined}
          />
        </>
      )}

      {pending && (
        <OperatorConfirmDialog
          open
          title={operatorConfirmTitle(pending)}
          description={operatorConfirmDescription(pending)}
          confirmLabel={operatorConfirmLabel(pending)}
          elevated={pending.elevated}
          roleEditor={
            pending.kind === 'edit-roles'
              ? { initialRoles: pending.operator?.roles ?? [] }
              : undefined
          }
          grantableRoles={grantableRoles}
          pending={activeMutation?.isPending ?? false}
          errorMessage={dialogError}
          onConfirm={onConfirm}
          onCancel={closeDialog}
        />
      )}

      {/* TASK-PC-FE-017 — admin-on-behalf-of profile edit dialog (sibling
          of OperatorConfirmDialog per § Decision authority "Why a
          separate dialog component"). TASK-PC-FE-018 — pre-populates the
          input with the operator's current operatorContext.defaultAccountId
          (read from the list response BE-308 extension). */}
      {profileEditFor && (
        <OperatorProfileEditDialog
          open
          operatorIdLabel={
            profileEditFor.email || profileEditFor.operatorId
          }
          initialDefaultAccountId={
            profileEditFor.operatorContext?.defaultAccountId ?? null
          }
          pending={setProfile.isPending}
          errorMessage={setProfileError}
          onConfirm={(defaultAccountId, reason) => {
            setProfile.mutate(
              {
                operatorId: profileEditFor.operatorId,
                defaultAccountId,
                reason,
              },
              { onSuccess: () => setProfileEditOperatorId(null) },
            );
          }}
          onCancel={() => setProfileEditOperatorId(null)}
        />
      )}

      {/* TASK-PC-FE-050 — org_scope (데이터-스코프) dialog. Reads the active-
          tenant assignment row + sets/clears its org_scope (tri-state
          전체/선택/차단). Mounted only when a target operator is chosen. */}
      {orgScopeFor && (
        <OrgScopeDialog
          open
          operatorId={orgScopeFor.operatorId}
          operatorLabel={orgScopeFor.email || orgScopeFor.operatorId}
          onClose={() => setOrgScopeOperatorId(null)}
        />
      )}
    </section>
  );
}
