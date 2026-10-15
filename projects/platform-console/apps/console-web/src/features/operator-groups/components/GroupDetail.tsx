'use client';

import { useState } from 'react';
import { Button } from '@/shared/ui/Button';
import { ApiError, messageForCode } from '@/shared/api/errors';
import {
  DOMAIN_SET_EFFECT_COPY,
  DOMAIN_SET_LATENCY_COPY,
} from '@/shared/api/domain-permission-sets-copy';
import { formatDateTime } from '@/shared/lib/datetime';
import type { Group, UpdateGroupInput } from '../api/types';
import {
  useGroupMembers,
  useGroupGrants,
  useUpdateGroup,
  useDeleteGroup,
  useAddMember,
  useRemoveMember,
  useAddGrants,
  useRemoveGrant,
} from '../hooks/use-operator-groups';
import {
  useGroupDomainSets,
  useAddGroupDomainSet,
  useRemoveGroupDomainSet,
} from '../hooks/use-group-domain-sets';
import { GroupForm } from './GroupForm';
import { GroupReasonDialog } from './GroupReasonDialog';
import { GroupMemberDialog } from './GroupMemberDialog';
import { GroupGrantDialog } from './GroupGrantDialog';
import { GroupDomainSetDialog } from './GroupDomainSetDialog';

/**
 * Operator-group detail panel (TASK-PC-FE-250). A `<dl>` summary, an inline
 * edit (`GroupForm` mode=edit → reason gate), a delete action, and two live
 * panels — 멤버 (`useGroupMembers`) + Grant 템플릿 (`useGroupGrants`), each with
 * an add dialog (member / grant) and per-row revoke, all reason-gated.
 *
 * Mount with `key={group.groupId}` from the screen so local editor / dialog
 * state resets cleanly when a different group is selected.
 */
export interface GroupDetailProps {
  group: Group;
  /** Caller's grantable roles (fetched by the screen); `null` ⇒ full known set. */
  grantableRoles: string[] | null;
  /** Called after a successful delete so the screen can clear the selection. */
  onDeleted: () => void;
}

function errText(err: unknown): string | null {
  if (!err) return null;
  if (err instanceof ApiError) return messageForCode(err.code, err.message);
  return '요청을 처리하지 못했습니다.';
}

export function GroupDetail({ group, grantableRoles, onDeleted }: GroupDetailProps) {
  const members = useGroupMembers(group.groupId);
  const grants = useGroupGrants(group.groupId);
  const domainSets = useGroupDomainSets(group.groupId);
  const update = useUpdateGroup();
  const remove = useDeleteGroup();
  const addMember = useAddMember();
  const removeMember = useRemoveMember();
  const addGrants = useAddGrants();
  const removeGrant = useRemoveGrant();
  const addDomainSet = useAddGroupDomainSet();
  const removeDomainSet = useRemoveGroupDomainSet();

  const [pendingEdit, setPendingEdit] = useState<UpdateGroupInput | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [addingMember, setAddingMember] = useState(false);
  const [addingGrant, setAddingGrant] = useState(false);
  const [addingDomainSet, setAddingDomainSet] = useState(false);
  const [revokeMemberTarget, setRevokeMemberTarget] = useState<string | null>(null);
  const [revokeGrantTarget, setRevokeGrantTarget] = useState<string | null>(null);
  const [revokeDomainSetTarget, setRevokeDomainSetTarget] = useState<string | null>(null);
  /** TASK-PC-FE-338 slice b — the last successful add's fan-out count, shown
   *  once as a dismissible notice (admin-api.md row 10 `fannedOutRows`). */
  const [lastFanOutCount, setLastFanOutCount] = useState<number | null>(null);

  const updateError = update.isError ? errText(update.error) : null;
  const hasDomainSets = (domainSets.data?.length ?? 0) > 0;

  return (
    <div className="space-y-6" data-testid="group-detail">
      <div className="flex items-center justify-between gap-3">
        <h2 className="text-xl font-semibold text-foreground" data-testid="group-detail-name">
          {group.name}
        </h2>
        <Button
          variant="ghost"
          onClick={() => setDeleting(true)}
          disabled={remove.isPending}
          data-testid="group-delete"
        >
          삭제
        </Button>
      </div>

      <dl className="grid grid-cols-[8rem_1fr] gap-x-4 gap-y-2 text-sm">
        <dt className="text-muted-foreground">테넌트</dt>
        <dd className="font-mono text-xs text-foreground">{group.tenantId}</dd>

        <dt className="text-muted-foreground">설명</dt>
        <dd className="text-foreground">
          {group.description ?? <span className="text-muted-foreground">(없음)</span>}
        </dd>

        <dt className="text-muted-foreground">멤버 / Grant</dt>
        <dd className="text-foreground" data-testid="group-detail-counts">
          멤버 {group.memberCount} · Grant {group.grantCount}
        </dd>

        <dt className="text-muted-foreground">그룹 ID</dt>
        <dd className="break-all font-mono text-xs text-foreground">{group.groupId}</dd>

        <dt className="text-muted-foreground">생성 / 수정</dt>
        <dd className="text-foreground">
          {formatDateTime(group.createdAt)} / {formatDateTime(group.updatedAt)}
        </dd>
      </dl>

      {remove.isError && (
        <p role="alert" data-testid="group-delete-error" className="text-sm text-destructive">
          {errText(remove.error)}
        </p>
      )}

      {/* rename / describe */}
      <GroupForm
        key={`${group.groupId}-${group.updatedAt}`}
        mode="edit"
        group={group}
        onSubmitUpdateDraft={(draft) => setPendingEdit(draft)}
        serverError={updateError}
        pending={update.isPending}
      />

      {/* members panel */}
      <section aria-labelledby="group-members-heading" className="space-y-3">
        <div className="flex items-center justify-between">
          <h3 id="group-members-heading" className="text-base font-semibold text-foreground">
            멤버
          </h3>
          <Button onClick={() => setAddingMember(true)} data-testid="group-member-add">
            멤버 추가
          </Button>
        </div>

        {/* TASK-PC-FE-338 slice b — ADR-083 「멤버 추가 · 업무 세트 그룹」
            note: adding a member to a group carrying ≥1 업무 권한 세트 ALSO
            needs the actor's grant scope to hold `domain_access.manage`
            (not just `group.manage`) — the producer answers
            `403 PERMISSION_DENIED` otherwise. The existing error path below
            already renders that inline via `messageForCode`; this is a
            proactive hint so the actor is not surprised by it. */}
        {hasDomainSets && (
          <p
            data-testid="group-member-domain-sets-hint"
            className="text-xs text-muted-foreground"
          >
            이 그룹에는 업무 권한 세트가 배정돼 있습니다 — 멤버 추가에는
            group.manage 외에 domain_access.manage 권한도 필요합니다.
          </p>
        )}

        {members.isError ? (
          <p role="alert" data-testid="group-members-error" className="text-sm text-destructive">
            {errText(members.error)}
          </p>
        ) : members.isLoading ? (
          <p className="text-sm text-muted-foreground">불러오는 중…</p>
        ) : (members.data?.length ?? 0) === 0 ? (
          <p className="text-sm text-muted-foreground" data-testid="group-members-empty">
            소속 멤버가 없습니다.
          </p>
        ) : (
          <ul
            data-testid="group-members-list"
            className="divide-y divide-border rounded-md border border-border"
          >
            {members.data?.map((m) => (
              <li
                key={m.operatorId}
                data-testid={`group-member-row-${m.operatorId}`}
                className="flex items-center justify-between gap-3 px-3 py-2 text-sm"
              >
                <span className="min-w-0">
                  <span className="font-medium text-foreground">{m.displayName}</span>{' '}
                  <span className="text-muted-foreground">
                    · {m.operatorId} · {formatDateTime(m.addedAt)}
                  </span>
                </span>
                <Button
                  variant="ghost"
                  onClick={() => setRevokeMemberTarget(m.operatorId)}
                  disabled={removeMember.isPending}
                  data-testid={`group-member-remove-${m.operatorId}`}
                >
                  제거
                </Button>
              </li>
            ))}
          </ul>
        )}
      </section>

      {/* 업무 권한 세트 panel (TASK-PC-FE-338 slice b — ADR-083, contract
          § 2.4.3.4 screen 3 / rows 9-11) */}
      <section aria-labelledby="group-domain-sets-heading" className="space-y-3">
        <div className="flex items-center justify-between">
          <h3
            id="group-domain-sets-heading"
            className="text-base font-semibold text-foreground"
          >
            업무 권한 세트
          </h3>
          <Button
            onClick={() => setAddingDomainSet(true)}
            data-testid="group-domain-set-add"
          >
            세트 추가
          </Button>
        </div>

        {/* Status honesty (contract § 2.4.3.4): P3 is live (TASK-BE-632), so a group set
            narrows each member's token to its roles ∩ the subscribed domains. The pre-P3
            «적용 예정» note was retired in TASK-PC-FE-338 c-2 PR 1. */}
        <p
          role="note"
          data-testid="group-domain-sets-effect-note"
          className="text-xs text-muted-foreground"
        >
          그룹의 세트는 모든 멤버에게 배정됩니다. {DOMAIN_SET_EFFECT_COPY}
        </p>

        {lastFanOutCount !== null && (
          <p
            role="status"
            data-testid="group-domain-set-fanout-notice"
            className="rounded-md border border-border bg-muted px-3 py-2 text-sm text-muted-foreground"
          >
            멤버 {lastFanOutCount}명에게 배정했습니다.{' '}
            <button
              type="button"
              className="underline"
              onClick={() => setLastFanOutCount(null)}
            >
              닫기
            </button>
          </p>
        )}

        {domainSets.isError ? (
          <p
            role="alert"
            data-testid="group-domain-sets-error"
            className="text-sm text-destructive"
          >
            {errText(domainSets.error)}
          </p>
        ) : domainSets.isLoading ? (
          <p className="text-sm text-muted-foreground">불러오는 중…</p>
        ) : (domainSets.data?.length ?? 0) === 0 ? (
          <p
            className="text-sm text-muted-foreground"
            data-testid="group-domain-sets-empty"
          >
            배정된 업무 권한 세트가 없습니다.
          </p>
        ) : (
          <ul
            data-testid="group-domain-sets-list"
            className="divide-y divide-border rounded-md border border-border"
          >
            {domainSets.data?.map((s) => (
              <li
                key={s.setId}
                data-testid={`group-domain-set-row-${s.setId}`}
                className="flex items-center justify-between gap-3 px-3 py-2 text-sm"
              >
                <span className="min-w-0">
                  <span className="font-medium text-foreground">{s.name}</span>{' '}
                  {s.systemKey && (
                    <span className="rounded bg-muted px-1.5 py-0.5 text-xs text-muted-foreground">
                      시스템
                    </span>
                  )}{' '}
                  <span className="text-muted-foreground">
                    · {formatDateTime(s.grantedAt)}
                  </span>
                </span>
                <Button
                  variant="ghost"
                  onClick={() => setRevokeDomainSetTarget(s.setId)}
                  disabled={removeDomainSet.isPending}
                  data-testid={`group-domain-set-remove-${s.setId}`}
                >
                  회수
                </Button>
              </li>
            ))}
          </ul>
        )}
      </section>

      {/* grants panel */}
      <section aria-labelledby="group-grants-heading" className="space-y-3">
        <div className="flex items-center justify-between">
          <h3 id="group-grants-heading" className="text-base font-semibold text-foreground">
            Grant 템플릿
          </h3>
          <Button onClick={() => setAddingGrant(true)} data-testid="group-grant-add">
            Grant 추가
          </Button>
        </div>

        {grants.isError ? (
          <p role="alert" data-testid="group-grants-error" className="text-sm text-destructive">
            {errText(grants.error)}
          </p>
        ) : grants.isLoading ? (
          <p className="text-sm text-muted-foreground">불러오는 중…</p>
        ) : (grants.data?.length ?? 0) === 0 ? (
          <p className="text-sm text-muted-foreground" data-testid="group-grants-empty">
            부여된 grant 가 없습니다.
          </p>
        ) : (
          <ul
            data-testid="group-grants-list"
            className="divide-y divide-border rounded-md border border-border"
          >
            {grants.data?.map((g) => (
              <li
                key={g.grantId}
                data-testid={`group-grant-row-${g.grantId}`}
                className="flex items-center justify-between gap-3 px-3 py-2 text-sm"
              >
                <span className="min-w-0">
                  {g.type === 'ROLE' ? (
                    <>
                      <span className="font-medium text-foreground">역할</span>{' '}
                      <span className="text-muted-foreground">· {g.roleName}</span>
                    </>
                  ) : (
                    <>
                      <span className="font-medium text-foreground">
                        tenant-assignment
                      </span>{' '}
                      <span className="text-muted-foreground">· {g.tenantId}</span>
                    </>
                  )}{' '}
                  <span className="text-muted-foreground">· {formatDateTime(g.grantedAt)}</span>
                </span>
                <Button
                  variant="ghost"
                  onClick={() => setRevokeGrantTarget(g.grantId)}
                  disabled={removeGrant.isPending}
                  data-testid={`group-grant-revoke-${g.grantId}`}
                >
                  회수
                </Button>
              </li>
            ))}
          </ul>
        )}
      </section>

      {/* --- confirm dialogs ------------------------------------------------ */}

      {pendingEdit && (
        <GroupReasonDialog
          title="그룹 정보 변경"
          description="그룹의 이름/설명을 변경합니다 (멤버 grant 는 변하지 않습니다)."
          confirmLabel="변경"
          pending={update.isPending}
          error={updateError}
          onConfirm={(reason) => {
            update.mutate(
              { groupId: group.groupId, patch: pendingEdit, reason },
              { onSuccess: () => setPendingEdit(null) },
            );
          }}
          onCancel={() => setPendingEdit(null)}
        />
      )}

      {deleting && (
        <GroupReasonDialog
          title="그룹 삭제"
          description="그룹을 삭제하면 이 그룹이 fan-out 한 멤버들의 권한이 회수됩니다 (멤버의 직접 grant 는 유지). 되돌릴 수 없습니다."
          confirmLabel="삭제"
          tone="destructive"
          pending={remove.isPending}
          error={remove.isError ? errText(remove.error) : null}
          onConfirm={(reason) => {
            remove.mutate(
              { groupId: group.groupId, reason },
              {
                onSuccess: () => {
                  setDeleting(false);
                  onDeleted();
                },
              },
            );
          }}
          onCancel={() => setDeleting(false)}
        />
      )}

      {addingMember && (
        <GroupMemberDialog
          groupName={group.name}
          groupTenantId={group.tenantId}
          existingMemberIds={members.data?.map((m) => m.operatorId) ?? []}
          hasDomainSets={hasDomainSets}
          pending={addMember.isPending}
          error={addMember.isError ? errText(addMember.error) : null}
          onConfirm={(operatorId, reason) => {
            const idempotencyKey =
              typeof crypto !== 'undefined' && 'randomUUID' in crypto
                ? crypto.randomUUID()
                : `group-member-${Date.now()}`;
            addMember.mutate(
              { groupId: group.groupId, operatorId, reason, idempotencyKey },
              { onSuccess: () => setAddingMember(false) },
            );
          }}
          onCancel={() => setAddingMember(false)}
        />
      )}

      {addingGrant && (
        <GroupGrantDialog
          groupName={group.name}
          grantableRoles={grantableRoles}
          pending={addGrants.isPending}
          error={addGrants.isError ? errText(addGrants.error) : null}
          onConfirm={(input, reason) => {
            const idempotencyKey =
              typeof crypto !== 'undefined' && 'randomUUID' in crypto
                ? crypto.randomUUID()
                : `group-grant-${Date.now()}`;
            addGrants.mutate(
              { groupId: group.groupId, input, reason, idempotencyKey },
              { onSuccess: () => setAddingGrant(false) },
            );
          }}
          onCancel={() => setAddingGrant(false)}
        />
      )}

      {revokeMemberTarget !== null && (
        <GroupReasonDialog
          title="멤버 제거"
          description={`운영자(${revokeMemberTarget})를 이 그룹에서 제거합니다. 이 그룹이 fan-out 한 권한만 회수되고 직접 grant 는 유지됩니다.`}
          confirmLabel="제거"
          tone="destructive"
          pending={removeMember.isPending}
          error={removeMember.isError ? errText(removeMember.error) : null}
          onConfirm={(reason) => {
            removeMember.mutate(
              { groupId: group.groupId, operatorId: revokeMemberTarget, reason },
              { onSuccess: () => setRevokeMemberTarget(null) },
            );
          }}
          onCancel={() => setRevokeMemberTarget(null)}
        />
      )}

      {revokeGrantTarget !== null && (
        <GroupReasonDialog
          title="Grant 회수"
          description="이 grant 로 fan-out 된 전 멤버의 권한이 회수됩니다 (멤버의 직접 grant 는 유지)."
          confirmLabel="회수"
          tone="destructive"
          pending={removeGrant.isPending}
          error={removeGrant.isError ? errText(removeGrant.error) : null}
          onConfirm={(reason) => {
            removeGrant.mutate(
              { groupId: group.groupId, grantId: revokeGrantTarget, reason },
              { onSuccess: () => setRevokeGrantTarget(null) },
            );
          }}
          onCancel={() => setRevokeGrantTarget(null)}
        />
      )}

      {/* 업무 권한 세트 add / remove (TASK-PC-FE-338 slice b) */}

      {addingDomainSet && (
        <GroupDomainSetDialog
          groupName={group.name}
          pending={addDomainSet.isPending}
          error={addDomainSet.isError ? errText(addDomainSet.error) : null}
          onConfirm={(setId, reason) => {
            const idempotencyKey =
              typeof crypto !== 'undefined' && 'randomUUID' in crypto
                ? crypto.randomUUID()
                : `group-domain-set-${Date.now()}`;
            addDomainSet.mutate(
              { groupId: group.groupId, setId, reason, idempotencyKey },
              {
                onSuccess: (result) => {
                  setAddingDomainSet(false);
                  setLastFanOutCount(result.fannedOutRows);
                },
              },
            );
          }}
          onCancel={() => setAddingDomainSet(false)}
        />
      )}

      {revokeDomainSetTarget !== null && (
        <GroupReasonDialog
          title="업무 권한 세트 회수"
          description={`"${
            domainSets.data?.find((s) => s.setId === revokeDomainSetTarget)?.name ??
            revokeDomainSetTarget
          }" 세트의 그룹 배정을 회수합니다 — 이 그룹이 fan-out 한 전 멤버의 권한이 회수됩니다(멤버의 직접·다른 그룹 배정은 유지). ${DOMAIN_SET_LATENCY_COPY}`}
          confirmLabel="회수"
          tone="destructive"
          pending={removeDomainSet.isPending}
          error={removeDomainSet.isError ? errText(removeDomainSet.error) : null}
          onConfirm={(reason) => {
            removeDomainSet.mutate(
              { groupId: group.groupId, setId: revokeDomainSetTarget, reason },
              { onSuccess: () => setRevokeDomainSetTarget(null) },
            );
          }}
          onCancel={() => setRevokeDomainSetTarget(null)}
        />
      )}
    </div>
  );
}
