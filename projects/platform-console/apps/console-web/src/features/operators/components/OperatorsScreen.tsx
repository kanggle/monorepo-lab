'use client';

import { useMemo, useState } from 'react';
import { ApiError, messageForCode } from '@/shared/api/errors';
import {
  useOperatorInvitations,
  useInviteOperator,
  useCancelInvitation,
  useResendInvitation,
} from '../hooks/use-operator-invitations';
import type {
  InviteOperatorInput,
  OperatorInvitation,
} from '../api/invitation-types';
import { InviteOperatorForm } from './InviteOperatorForm';
import { OperatorInvitationsSection } from './OperatorInvitationsSection';
import { InvitationDeliveryNotice } from './InvitationDeliveryNotice';
import {
  INVITATION_STALE_CODES,
  errorCode,
  invitationErrorMessage,
} from './invitation-copy';
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
  OperatorsPermissionDenied,
  OperatorsDegradedNotice,
} from './OperatorsListNotices';
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
 * ── «등록» → «초대» (TASK-MONO-772 S5 — § 2.4.3 rows 2, 11–14) ── a company
 * (non-`*`) operator is no longer created here; it is INVITED into the ACTIVE
 * tenant and becomes an operator only when the invitee accepts on the IdP
 * with an account whose email they VERIFIED. The direct «등록» form stays
 * ONLY for a platform-scope operator and ONLY for `tenantId='*'`. The
 * pending-invitations list (cancel / resend) sits under the invite form; its
 * query key carries the active tenant (TASK-MONO-780 lesson) and lives under
 * the `['operators']` root the tenant switch invalidates. A FAILED_* mail
 * delivery is a WARNING with «다시 보내기», never an error — the invitation
 * exists. No token or link is ever rendered (the producer never sends one).
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
  /** True ⇒ this operator is platform-scope → the `*`-only «플랫폼 운영자
   *  등록» form is offered (TASK-MONO-772: the only direct create left). */
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

  // --- TASK-MONO-772 S5 — invitations (active-tenant scoped) ---------------
  // `*` is never an invitation target; the list still reads for `*` (the
  // producer answers platform-scope operators with every tenant's rows).
  const canInvite = activeTenant !== null && activeTenant !== '*';
  const [invitationsPage, setInvitationsPage] = useState(0);
  const invitations = useOperatorInvitations(activeTenant, {
    status: 'PENDING',
    page: invitationsPage,
    size: 20,
  });
  const invite = useInviteOperator();
  const cancelInvitation = useCancelInvitation();
  const resendInvitation = useResendInvitation();
  /** The last invite / resend response — drives the delivery notice. */
  const [deliveryNotice, setDeliveryNotice] = useState<{
    invitation: OperatorInvitation;
    via: 'invite' | 'resend';
  } | null>(null);
  /** «The row moved under you» answer of a cancel / resend (list refreshed). */
  const [invitationNotice, setInvitationNotice] = useState<string | null>(null);
  const [inviteResetSignal, setInviteResetSignal] = useState(0);
  const [lastInviteEmail, setLastInviteEmail] = useState<string | null>(null);
  const [highlightInvitationId, setHighlightInvitationId] = useState<
    string | null
  >(null);

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
      case 'invite':
        return invite;
      case 'cancel-invitation':
        return cancelInvitation;
      case 'resend-invitation':
        return resendInvitation;
      default:
        return null;
    }
  }, [
    pending?.kind,
    create,
    editRoles,
    changeStatus,
    assign,
    unassign,
    invite,
    cancelInvitation,
    resendInvitation,
  ]);

  const isInvitationKind =
    pending?.kind === 'invite' ||
    pending?.kind === 'cancel-invitation' ||
    pending?.kind === 'resend-invitation';

  // 403 (permission / tenant-scope) on the LIST read surfaces as ApiError
  // — render the whole section as inline "not permitted", never crash,
  // never a re-login loop.
  const listApiError =
    list.error instanceof ApiError ? (list.error as ApiError) : null;
  const permissionDenied = listApiError?.status === 403;
  const degraded =
    list.isError && (!listApiError || listApiError.status >= 500);

  const dialogError = isInvitationKind
    ? invitationErrorMessage(activeMutation?.error)
    : activeMutation?.error instanceof ApiError
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
        ? '운영자 등록에 실패했습니다.'
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

  // «already pending» → the contract asks for a jump to THAT row's resend.
  // Resolved against the loaded page (best effort — on another page the
  // message alone points at the list).
  const inviteErrorCode = errorCode(invite.error);
  const inviteError = invitationErrorMessage(invite.error);
  const pendingTwin =
    inviteErrorCode === 'OPERATOR_INVITATION_ALREADY_PENDING' && lastInviteEmail
      ? (invitations.data?.content.find(
          (i) => i.email.toLowerCase() === lastInviteEmail,
        ) ?? null)
      : null;

  function resetMutations() {
    create.reset();
    editRoles.reset();
    changeStatus.reset();
    assign.reset();
    unassign.reset();
    invite.reset();
    cancelInvitation.reset();
    resendInvitation.reset();
  }

  /** Inviter column label — display name from the loaded operators page
   *  (never the email: no second copy of an operator's address on screen). */
  function operatorLabel(operatorId: string): string {
    if (operatorId === selfOperatorId) return '나';
    const op = page?.content.find((o) => o.operatorId === operatorId);
    if (op) return op.displayName || op.operatorId;
    return operatorId.length > 12 ? `${operatorId.slice(0, 8)}…` : operatorId;
  }

  function openInvite(draft: InviteOperatorInput) {
    resetMutations();
    setInvitationNotice(null);
    setLastInviteEmail(draft.email.toLowerCase());
    setPending({
      kind: 'invite',
      inviteDraft: draft,
      idempotencyKey: newIdemKey(),
      // Granting operator access is privilege-high — same posture as create.
      elevated: true,
    });
  }

  function openCancelInvitation(invitation: OperatorInvitation) {
    resetMutations();
    setInvitationNotice(null);
    setPending({ kind: 'cancel-invitation', invitation, elevated: false });
  }

  function openResendInvitation(invitation: OperatorInvitation) {
    resetMutations();
    setInvitationNotice(null);
    setPending({ kind: 'resend-invitation', invitation, elevated: false });
  }

  /** A cancel / resend answered «stale» → close the dialog, say so; the hook
   *  already re-fetched the list (invalidate on settle). */
  function onInvitationActionError(err: unknown) {
    const code = errorCode(err);
    if (code !== null && INVITATION_STALE_CODES.has(code)) {
      setPending(null);
      setInvitationNotice(invitationErrorMessage(err));
    }
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
    if (
      pending.kind === 'invite' &&
      pending.inviteDraft &&
      pending.idempotencyKey
    ) {
      invite.mutate(
        {
          input: pending.inviteDraft,
          reason,
          // Reused verbatim if THIS confirmed invite is retried.
          idempotencyKey: pending.idempotencyKey,
        },
        {
          onSuccess: (invitation) => {
            setPending(null);
            // 201 — even a FAILED_* delivery is a success of the invitation.
            setDeliveryNotice({ invitation, via: 'invite' });
            setHighlightInvitationId(invitation.invitationId);
            setInviteResetSignal((n) => n + 1);
            setInvitationsPage(0);
          },
          onError: (err) => {
            // The two «this email is already handled» answers belong next to
            // the form (with the jump-to-resend), not inside the dialog.
            const code = errorCode(err);
            if (
              code === 'OPERATOR_INVITATION_ALREADY_PENDING' ||
              code === 'OPERATOR_EMAIL_CONFLICT'
            ) {
              setPending(null);
            }
          },
        },
      );
      return;
    }
    if (pending.kind === 'cancel-invitation' && pending.invitation) {
      const target = pending.invitation;
      cancelInvitation.mutate(
        { invitationId: target.invitationId, reason },
        {
          onSuccess: () => {
            setPending(null);
            setDeliveryNotice((n) =>
              n?.invitation.invitationId === target.invitationId ? null : n,
            );
          },
          onError: onInvitationActionError,
        },
      );
      return;
    }
    if (pending.kind === 'resend-invitation' && pending.invitation) {
      resendInvitation.mutate(
        { invitationId: pending.invitation.invitationId, reason },
        {
          onSuccess: (invitation) => {
            setPending(null);
            setDeliveryNotice({ invitation, via: 'resend' });
            setHighlightInvitationId(invitation.invitationId);
          },
          onError: onInvitationActionError,
        },
      );
      return;
    }
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
        운영자 초대·역할 변경·상태 변경·테넌트 배정. 회사 운영자는 이메일로
        초대하고, 받은 사람이 인증한 계정으로 수락해야 만들어집니다. 모든 변경
        작업은 사유와 확인이 필요하며 감사 기록에 남습니다. (operator.manage
        권한 필요)
      </p>

      {permissionDenied ? (
        <OperatorsPermissionDenied code={listApiError?.code} />
      ) : degraded ? (
        <OperatorsDegradedNotice />
      ) : (
        <>
          {/* TASK-MONO-772 S5 — «초대» (company operators, active tenant). */}
          {canInvite && activeTenant && (
            <InviteOperatorForm
              tenantId={activeTenant}
              onSubmitDraft={openInvite}
              serverError={pending?.kind === 'invite' ? null : inviteError}
              onJumpToPending={
                pendingTwin
                  ? () => {
                      setHighlightInvitationId(pendingTwin.invitationId);
                      openResendInvitation(pendingTwin);
                    }
                  : null
              }
              pending={invite.isPending}
              grantableRoles={grantableRoles}
              resetSignal={inviteResetSignal}
            />
          )}

          {deliveryNotice && (
            <InvitationDeliveryNotice
              invitation={deliveryNotice.invitation}
              via={deliveryNotice.via}
              onResend={() => openResendInvitation(deliveryNotice.invitation)}
              onDismiss={() => setDeliveryNotice(null)}
            />
          )}

          {activeTenant && (
            <>
              {invitationNotice && (
                <p
                  role="status"
                  data-testid="operator-invitations-notice"
                  className="mb-3 rounded-md border border-border bg-muted px-3 py-2 text-sm text-foreground"
                >
                  {invitationNotice}
                </p>
              )}
              <OperatorInvitationsSection
                tenantId={activeTenant}
                data={invitations.data}
                isLoading={invitations.isLoading}
                error={invitations.error}
                page={invitationsPage}
                onPrevPage={() => setInvitationsPage((p) => Math.max(0, p - 1))}
                onNextPage={() => setInvitationsPage((p) => p + 1)}
                onRetry={() => void invitations.refetch()}
                onCancel={openCancelInvitation}
                onResend={openResendInvitation}
                operatorLabel={operatorLabel}
                highlightInvitationId={highlightInvitationId}
                busy={cancelInvitation.isPending || resendInvitation.isPending}
              />
            </>
          )}

          {/* Direct create — platform scope (`*`) ONLY (§ 2.4.3 row 2). */}
          {isPlatformOperator && (
            <CreateOperatorForm
              onSubmitDraft={openCreate}
              serverError={createError}
              pending={create.isPending}
              grantableRoles={grantableRoles}
            />
          )}

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
