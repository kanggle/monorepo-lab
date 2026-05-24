'use client';

import { useMemo, useState } from 'react';
import { Button } from '@/shared/ui/Button';
import { ApiError, messageForCode } from '@/shared/api/errors';
import {
  useOperatorsList,
  useCreateOperator,
  useEditOperatorRoles,
  useChangeOperatorStatus,
  useChangeOwnPassword,
  useUpdateOwnProfile,
  useSetOperatorProfile,
} from '../hooks/use-operators';
import {
  OPERATOR_STATUSES,
  ELEVATED_ROLE,
  type OperatorPage,
  type OperatorSummary,
  type OperatorStatus,
  type CreateOperatorInput,
} from '../api/types';
import { OperatorRoleChips, OperatorStatusBadge } from './OperatorBadges';
import { CreateOperatorForm } from './CreateOperatorForm';
import { ChangePasswordForm } from './ChangePasswordForm';
import { MyProfileForm } from './MyProfileForm';
import { OperatorConfirmDialog } from './OperatorConfirmDialog';
import { OperatorProfileEditDialog } from './OperatorProfileEditDialog';

/**
 * GAP operators-management surface (TASK-PC-FE-004 — Phase 2 slice 3, the
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
 */

type PendingKind = 'create' | 'edit-roles' | 'change-status';

interface PendingAction {
  kind: PendingKind;
  operator?: OperatorSummary;
  /** create draft. */
  draft?: CreateOperatorInput;
  /** change-status target value. */
  nextStatus?: OperatorStatus;
  /** create only — stable across retries of THIS confirmed create. */
  idempotencyKey?: string;
  /** Privilege-high → elevated confirm copy. */
  elevated: boolean;
}

function newIdemKey(): string {
  const g = globalThis as unknown as {
    crypto?: { randomUUID?: () => string };
  };
  return (
    g.crypto?.randomUUID?.() ??
    `idem-${Date.now()}-${Math.random().toString(36).slice(2)}`
  );
}

export interface OperatorsScreenProps {
  initial: OperatorPage;
  /** Tenants the operator may assign on create (from the catalog/registry). */
  tenantOptions?: string[];
  /** True ⇒ this operator is platform-scope → may offer `*` on create. */
  isPlatformOperator?: boolean;
  /** TASK-PC-FE-016 — server-rendered initial value for the self update-
   *  profile form (`operatorContext.defaultAccountId` from the catalog
   *  registry; null when never set). Passed verbatim to {@link MyProfileForm}. */
  initialDefaultAccountId?: string | null;
  /** TASK-PC-FE-017 — caller's own `operatorId` (when derivable). When set,
   *  the per-row "Profile 편집" button is disabled on the self row (UX gate;
   *  the producer's `400 SELF_PROFILE_UPDATE_FORBIDDEN_VIA_ADMIN_PATH` is
   *  the fail-safe). `null` ⇒ gate inactive — producer is still authoritative. */
  selfOperatorId?: string | null;
}

export function OperatorsScreen({
  initial,
  tenantOptions = [],
  isPlatformOperator = false,
  initialDefaultAccountId = null,
  selfOperatorId = null,
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
  const changePw = useChangeOwnPassword();
  const updateProfile = useUpdateOwnProfile();
  const setProfile = useSetOperatorProfile();

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

  const activeMutation = useMemo(() => {
    switch (pending?.kind) {
      case 'create':
        return create;
      case 'edit-roles':
        return editRoles;
      case 'change-status':
        return changeStatus;
      default:
        return null;
    }
  }, [pending?.kind, create, editRoles, changeStatus]);

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
        ? '운영자 생성에 실패했습니다.'
        : null;

  const pwError =
    changePw.error instanceof ApiError
      ? messageForCode(
          (changePw.error as ApiError).code,
          changePw.error.message,
        )
      : changePw.error
        ? '비밀번호 변경에 실패했습니다.'
        : null;

  const updateProfileError =
    updateProfile.error instanceof ApiError
      ? messageForCode(
          (updateProfile.error as ApiError).code,
          updateProfile.error.message,
        )
      : updateProfile.error
        ? '프로파일 저장에 실패했습니다.'
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
        운영자 생성·역할 변경·상태 변경·내 비밀번호 변경. 모든 변경 작업은
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
          />

          <ChangePasswordForm
            onSubmit={(currentPassword, newPassword) =>
              changePw.mutate({ currentPassword, newPassword })
            }
            serverError={pwError}
            pending={changePw.isPending}
            succeeded={changePw.isSuccess}
          />

          <MyProfileForm
            initial={initialDefaultAccountId}
            onSubmit={(defaultAccountId) =>
              updateProfile.mutate({ defaultAccountId })
            }
            serverError={updateProfileError}
            pending={updateProfile.isPending}
            succeeded={updateProfile.isSuccess}
          />

          <form
            onSubmit={submitFilter}
            className="mb-6 flex flex-wrap items-end gap-3"
            role="search"
            aria-label="운영자 목록 필터"
          >
            <div>
              <label
                htmlFor="operators-status-filter"
                className="block text-sm font-medium text-foreground"
              >
                상태 필터
              </label>
              <select
                id="operators-status-filter"
                value={statusFilter}
                onChange={(e) =>
                  setStatusFilter(
                    e.target.value as '' | OperatorStatus,
                  )
                }
                data-testid="operators-status-filter"
                className="mt-1 w-48 rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
              >
                <option value="">전체</option>
                {OPERATOR_STATUSES.map((s) => (
                  <option key={s} value={s}>
                    {s}
                  </option>
                ))}
              </select>
            </div>
            <Button type="submit" data-testid="operators-filter-submit">
              조회
            </Button>
          </form>

          {list.isError && (
            <div
              role="status"
              data-testid="operators-degraded"
              className="mb-6 rounded-md border border-border bg-muted px-4 py-3 text-sm text-muted-foreground"
            >
              운영자 목록을 일시적으로 불러올 수 없습니다. 잠시 후 다시
              시도하세요.
            </div>
          )}

          {rows.length === 0 ? (
            <p
              className="text-sm text-muted-foreground"
              data-testid="operators-empty"
            >
              표시할 운영자가 없습니다.
            </p>
          ) : (
            <>
              <table
                className="w-full border-collapse text-sm"
                data-testid="operators-table"
              >
                <caption className="sr-only">운영자 목록</caption>
                <thead>
                  <tr className="border-b border-border text-left">
                    <th scope="col" className="p-2">
                      이메일
                    </th>
                    <th scope="col" className="p-2">
                      표시 이름
                    </th>
                    <th scope="col" className="p-2">
                      상태
                    </th>
                    <th scope="col" className="p-2">
                      역할
                    </th>
                    <th scope="col" className="p-2">
                      생성일
                    </th>
                    <th scope="col" className="p-2">
                      작업
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((op) => (
                    <tr
                      key={op.operatorId}
                      data-testid={`operator-row-${op.operatorId}`}
                      className="border-b border-border"
                    >
                      <td className="p-2">{op.email}</td>
                      <td className="p-2">{op.displayName}</td>
                      <td className="p-2">
                        <OperatorStatusBadge status={op.status} />
                      </td>
                      <td className="p-2">
                        <OperatorRoleChips roles={op.roles} />
                      </td>
                      <td className="p-2 text-muted-foreground">
                        {op.createdAt}
                      </td>
                      <td className="p-2">
                        <div className="flex flex-wrap gap-2">
                          <Button
                            variant="secondary"
                            onClick={() => openEditRoles(op)}
                            data-testid={`action-edit-roles-${op.operatorId}`}
                          >
                            역할 변경
                          </Button>
                          <Button
                            variant="secondary"
                            className={
                              op.status === 'SUSPENDED'
                                ? undefined
                                : 'text-destructive'
                            }
                            onClick={() => openChangeStatus(op)}
                            data-testid={`action-status-${op.operatorId}`}
                          >
                            {op.status === 'SUSPENDED'
                              ? '활성화'
                              : '정지'}
                          </Button>
                          {/* TASK-PC-FE-017 — admin-on-behalf-of profile edit.
                              Disabled on self row (producer-side rejection
                              `400 SELF_PROFILE_UPDATE_FORBIDDEN_VIA_ADMIN_PATH`
                              is the fail-safe; UX layer hides the always-400). */}
                          <Button
                            variant="secondary"
                            disabled={
                              selfOperatorId !== null &&
                              selfOperatorId === op.operatorId
                            }
                            title={
                              selfOperatorId !== null &&
                              selfOperatorId === op.operatorId
                                ? '자기 자신은 /operators 의 "내 프로파일" 영역에서 변경하세요.'
                                : undefined
                            }
                            onClick={() => {
                              setProfile.reset();
                              setProfileEditOperatorId(op.operatorId);
                            }}
                            data-testid={`action-edit-profile-${op.operatorId}`}
                          >
                            프로파일 편집
                          </Button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>

              <nav
                className="mt-4 flex items-center justify-between"
                aria-label="페이지 이동"
              >
                <Button
                  variant="secondary"
                  disabled={query.page <= 0}
                  onClick={() =>
                    setQuery((q) => ({
                      ...q,
                      page: Math.max(0, q.page - 1),
                    }))
                  }
                  data-testid="operators-prev"
                >
                  이전
                </Button>
                <span
                  className="text-sm text-muted-foreground"
                  data-testid="operators-pageinfo"
                >
                  {page
                    ? `${page.page + 1} / ${Math.max(1, page.totalPages)} 페이지 · 총 ${page.totalElements}명`
                    : '—'}
                </span>
                <Button
                  variant="secondary"
                  disabled={
                    !page || page.page + 1 >= page.totalPages
                  }
                  onClick={() =>
                    setQuery((q) => ({ ...q, page: q.page + 1 }))
                  }
                  data-testid="operators-next"
                >
                  다음
                </Button>
              </nav>
            </>
          )}
        </>
      )}

      {pending && (
        <OperatorConfirmDialog
          open
          title={
            pending.kind === 'create'
              ? '운영자 생성 (특권 작업)'
              : pending.kind === 'edit-roles'
                ? '운영자 역할 변경 (특권 작업)'
                : pending.nextStatus === 'SUSPENDED'
                  ? '운영자 정지 (특권 작업)'
                  : '운영자 활성화'
          }
          description={
            pending.kind === 'create' ? (
              <>
                <strong>{pending.draft?.email}</strong> 운영자를{' '}
                <strong>{pending.draft?.tenantId}</strong> 테넌트에
                생성합니다.
                {pending.draft?.roles.includes(ELEVATED_ROLE) && (
                  <>
                    {' '}
                    이 운영자는 <strong>SUPER_ADMIN</strong> 특권을
                    가집니다.
                  </>
                )}{' '}
                이 작업은 운영자 권한 부여에 해당하므로 사유가 요구됩니다.
              </>
            ) : pending.kind === 'edit-roles' ? (
              <>
                <strong>{pending.operator?.email}</strong> 운영자의 역할을
                전체 교체합니다. 역할을 모두 비우면 이 운영자는 어떤 운영
                권한도 갖지 않으며, <strong>SUPER_ADMIN</strong> 부여는
                특권 상승입니다. 사유가 요구됩니다.
              </>
            ) : pending.nextStatus === 'SUSPENDED' ? (
              <>
                <strong>{pending.operator?.email}</strong> 운영자를{' '}
                <strong>정지(SUSPENDED)</strong> 합니다. 정지 시 해당
                운영자의 모든 세션이 즉시 종료됩니다. 사유가 요구됩니다.
              </>
            ) : (
              <>
                <strong>{pending.operator?.email}</strong> 운영자를
                활성화(ACTIVE)합니다. 사유가 요구됩니다.
              </>
            )
          }
          confirmLabel={
            pending.kind === 'create'
              ? '운영자 생성'
              : pending.kind === 'edit-roles'
                ? '역할 변경'
                : pending.nextStatus === 'SUSPENDED'
                  ? '정지'
                  : '활성화'
          }
          elevated={pending.elevated}
          roleEditor={
            pending.kind === 'edit-roles'
              ? { initialRoles: pending.operator?.roles ?? [] }
              : undefined
          }
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
    </section>
  );
}
