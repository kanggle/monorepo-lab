'use client';

import { useMemo, useState } from 'react';
import { ApiError, messageForCode } from '@/shared/api/errors';
import {
  useOperatorInvitations,
  useInviteOperator,
  useCancelInvitation,
  useResendInvitation,
} from './use-operator-invitations';
import type {
  InviteOperatorInput,
  OperatorInvitation,
} from '../api/invitation-types';
import {
  INVITATION_STALE_CODES,
  errorCode,
  invitationErrorMessage,
} from '../components/invitation-copy';
import {
  useOperatorsList,
  useCreateOperator,
  useEditOperatorRoles,
  useChangeOperatorStatus,
  useSetOperatorProfile,
  useAssignOperator,
  useUnassignOperator,
} from './use-operators';
import {
  type OperatorSummary,
  type OperatorStatus,
  type CreateOperatorInput,
} from '../api/types';
import { type PendingAction, newIdemKey } from '../components/operators-confirm-copy';
import type { OperatorsScreenProps } from '../components/OperatorsScreen';

/**
 * State, mutations, derived values and handlers of the operators-management
 * screen — moved verbatim out of `OperatorsScreen.tsx` (TASK-PC-FE-387, a
 * pure MOVE: hook order and dependency arrays are unchanged). The screen
 * component is display-only and destructures this return value.
 */
export function useOperatorsScreen({
  initial,
  isPlatformOperator = false,
  selfOperatorId = null,
  activeTenant = null,
}: OperatorsScreenProps) {
  const [statusFilter, setStatusFilter] = useState<'' | OperatorStatus>('');
  const [query, setQuery] = useState<{
    status?: OperatorStatus;
    page: number;
    size: number;
  }>({ page: initial.page, size: initial.size });

  // TASK-PC-FE-354 — «모든 테넌트» view: a platform-scope caller ONLY (the
  // page's `isPlatformOperator`). `'*'` operators hold no assignment row, so
  // the all-tenants listing (`tenantId='*'`) is the only place they appear.
  const [allTenantsView, setAllTenantsView] = useState(false);
  const showAllTenants = isPlatformOperator && allTenantsView;

  const seeded =
    query.page === initial.page &&
    query.status === undefined &&
    !showAllTenants;
  const list = useOperatorsList(
    showAllTenants ? { ...query, tenantId: '*' } : query,
    seeded ? initial : undefined,
  );
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

  // TASK-PC-FE-338 slice b — ADR-MONO-083 「업무 권한」 dialog target, tracked
  // by operatorId (reactive against the live list query, same posture as the
  // profile-edit / org-scope dialogs above).
  const [domainAccessOperatorId, setDomainAccessOperatorId] = useState<
    string | null
  >(null);
  const domainAccessFor = useMemo<OperatorSummary | null>(
    () =>
      domainAccessOperatorId === null
        ? null
        : (page?.content.find(
            (o: OperatorSummary) => o.operatorId === domainAccessOperatorId,
          ) ?? null),
    [page, domainAccessOperatorId],
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
      // TASK-MONO-784 S6 — edit-roles replaces only the ACTIVE company's
      // direct roles (the header the producer already reads); name it in
      // the confirm copy so the operator isn't surprised other companies
      // are untouched.
      // TASK-PC-FE-354 — a '*' target's roles are replaced in '*' whatever
      // the header (`PatchOperatorRoleUseCase` resolveActingTenant), so the
      // copy names '*' for it instead of the active company.
      tenantId:
        operator.homeTenantId === '*' ? '*' : (activeTenant ?? undefined),
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
      // TASK-MONO-784 S6 — a platform-scope ('*') actor's suspend reaches
      // every one of the target's companies (row-wide — R3); say so.
      // TASK-PC-FE-354 — not for a '*' target: it belongs to no company, so
      // «여러 회사에 소속돼 있습니다» would be false (its suspension is still
      // row-wide; the base copy says so without the membership claim).
      allCompanies:
        nextStatus === 'SUSPENDED' &&
        isPlatformOperator &&
        operator.homeTenantId !== '*',
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
  return {
    permissionDenied,
    listApiError,
    degraded,
    canInvite,
    openInvite,
    inviteError,
    pending,
    pendingTwin,
    setHighlightInvitationId,
    openResendInvitation,
    invite,
    inviteResetSignal,
    deliveryNotice,
    setDeliveryNotice,
    invitationNotice,
    invitations,
    invitationsPage,
    setInvitationsPage,
    openCancelInvitation,
    operatorLabel,
    highlightInvitationId,
    cancelInvitation,
    resendInvitation,
    create,
    createError,
    openCreate,
    assign,
    openAssign,
    showAllTenants,
    setAllTenantsView,
    setQuery,
    statusFilter,
    setStatusFilter,
    submitFilter,
    list,
    rows,
    page,
    query,
    openEditRoles,
    openChangeStatus,
    openProfileEdit,
    setOrgScopeOperatorId,
    setDomainAccessOperatorId,
    openUnassign,
    activeMutation,
    dialogError,
    onConfirm,
    closeDialog,
    profileEditFor,
    setProfile,
    setProfileError,
    setProfileEditOperatorId,
    orgScopeFor,
    domainAccessFor,
  };
}
