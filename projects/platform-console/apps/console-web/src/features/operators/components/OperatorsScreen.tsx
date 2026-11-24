'use client';

import { Button } from '@/shared/ui/Button';
import { InviteOperatorForm } from './InviteOperatorForm';
import { OperatorInvitationsSection } from './OperatorInvitationsSection';
import { InvitationDeliveryNotice } from './InvitationDeliveryNotice';
import { type OperatorPage } from '../api/types';
import { CreateOperatorForm } from './CreateOperatorForm';
import { AssignOperatorForm } from './AssignOperatorForm';
import { OperatorConfirmDialog } from './OperatorConfirmDialog';
import { OperatorProfileEditDialog } from './OperatorProfileEditDialog';
import { OrgScopeDialog } from './OrgScopeDialog';
import { OperatorDomainAccessDialog } from './OperatorDomainAccessDialog';
import { OperatorsTable } from './OperatorsTable';
import {
  OperatorsPermissionDenied,
  OperatorsDegradedNotice,
} from './OperatorsListNotices';
import {
  operatorConfirmTitle,
  operatorConfirmDescription,
  operatorConfirmLabel,
} from './operators-confirm-copy';
import { useOperatorsScreen } from '../hooks/use-operators-screen';

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
 *
 * ── HOOK SPLIT (TASK-PC-FE-387) ── state / mutations / handlers live in
 * `../hooks/use-operators-screen.ts`; this file is the display-only JSX.
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
  /** TASK-PC-FE-338 slice b — the active tenant's subscribed domains (`app/`
   *  layer, same source as `(console)/layout.tsx`'s sidebar badge and the
   *  `/domain-permission-sets` page). `null` ⇒ unknown — the 업무 권한
   *  dialog never marks a role «구독 없음» in that case. */
  subscribedDomains?: string[] | null;
}

export function OperatorsScreen({
  initial,
  isPlatformOperator = false,
  selfOperatorId = null,
  activeTenant = null,
  grantableRoles = null,
  subscribedDomains = null,
}: OperatorsScreenProps) {
  const {
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
  } = useOperatorsScreen({
    initial,
    isPlatformOperator,
    selfOperatorId,
    activeTenant,
    grantableRoles,
    subscribedDomains,
  });

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
              inviteAvailable={canInvite}
            />
          )}

          {/* TASK-PC-FE-354 — the view toggle, platform-scope callers only. */}
          {isPlatformOperator && (
            <div className="mb-3 space-y-2">
              <div
                role="group"
                aria-label="운영자 목록 범위"
                data-testid="operators-view-toggle"
                className="flex gap-2"
              >
                <Button
                  size="sm"
                  variant={showAllTenants ? 'secondary' : 'primary'}
                  aria-pressed={!showAllTenants}
                  onClick={() => {
                    setAllTenantsView(false);
                    setQuery((q) => ({ ...q, page: 0 }));
                  }}
                  data-testid="operators-view-tenant"
                >
                  현재 테넌트
                </Button>
                <Button
                  size="sm"
                  variant={showAllTenants ? 'primary' : 'secondary'}
                  aria-pressed={showAllTenants}
                  onClick={() => {
                    setAllTenantsView(true);
                    setQuery((q) => ({ ...q, page: 0 }));
                  }}
                  data-testid="operators-view-all-tenants"
                >
                  모든 테넌트
                </Button>
              </div>
              {showAllTenants && (
                <p
                  role="note"
                  data-testid="operators-all-tenants-note"
                  className="text-xs text-muted-foreground"
                >
                  모든 테넌트의 운영자입니다. 회사 운영자의 역할·조직 스코프·업무
                  권한·내보내기는 그 회사를 활성 테넌트로 고른 뒤 바꾸세요.
                </p>
              )}
            </div>
          )}

          <OperatorsTable
            allTenantsView={showAllTenants}
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
            onDomainAccess={
              activeTenant
                ? (op) => setDomainAccessOperatorId(op.operatorId)
                : undefined
            }
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
              ? {
                  initialRoles: pending.operator?.roles ?? [],
                  // TASK-PC-FE-354 — '*' target ⇒ '*'-wide editor copy.
                  allTenants: pending.tenantId === '*',
                }
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

      {/* TASK-PC-FE-338 slice b — ADR-MONO-083 「업무 권한」 dialog (contract
          § 2.4.3.4 screen 2). Per-set sources + revoke (DIRECT/MIGRATION
          only) + grant; subscription marking on the effective roles. */}
      {domainAccessFor && (
        <OperatorDomainAccessDialog
          open
          operatorId={domainAccessFor.operatorId}
          operatorLabel={domainAccessFor.email || domainAccessFor.operatorId}
          isSelf={
            selfOperatorId !== null &&
            selfOperatorId === domainAccessFor.operatorId
          }
          subscribedDomains={subscribedDomains}
          targetIsPlatform={domainAccessFor.homeTenantId === '*'}
          callerIsPlatform={isPlatformOperator}
          onClose={() => setDomainAccessOperatorId(null)}
        />
      )}
    </section>
  );
}
