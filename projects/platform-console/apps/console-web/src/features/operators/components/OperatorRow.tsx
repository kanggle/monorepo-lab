'use client';

import { Button } from '@/shared/ui/Button';
import type { OperatorSummary } from '../api/types';
import { formatDateTime } from '@/shared/lib/datetime';
import { OperatorRoleChips, OperatorStatusBadge } from './OperatorBadges';

/**
 * A single operators-table row (TASK-PC-FE-209 split of `OperatorsTable`) —
 * the identity cells + the per-row privilege-sensitive action buttons (역할
 * 변경 / 정지·활성화 / 프로파일 편집 / 조직 스코프 / 이 회사에서 내보내기
 * — TASK-MONO-784 S6 renamed the last one from «배정 해제»). Pure
 * presentation: all action handlers live in the `OperatorsScreen` container
 * and arrive via props.
 */
export interface OperatorRowProps {
  op: OperatorSummary;
  /** Caller's own operatorId — the per-row "프로파일 편집" is disabled on the
   *  self row (producer 400 is the fail-safe). `null` ⇒ gate inactive. */
  selfOperatorId: string | null;
  onEditRoles: (op: OperatorSummary) => void;
  onChangeStatus: (op: OperatorSummary) => void;
  onEditProfile: (op: OperatorSummary) => void;
  onOrgScope: (op: OperatorSummary) => void;
  /** TASK-PC-FE-338 slice b — ADR-MONO-083 「업무 권한」 (contract § 2.4.3.4
   *  screen 2). Omitted (⇒ button hidden) when no active tenant is resolved
   *  (mirrors `onUnassign` — the surface is tenant-scoped). */
  onDomainAccess?: (op: OperatorSummary) => void;
  /** TASK-PC-FE-157 — remove this operator's assignment to the active tenant.
   *  Omitted (⇒ button hidden) when no active tenant is resolved. */
  onUnassign?: (op: OperatorSummary) => void;
}

export function OperatorRow({
  op,
  selfOperatorId,
  onEditRoles,
  onChangeStatus,
  onEditProfile,
  onOrgScope,
  onDomainAccess,
  onUnassign,
}: OperatorRowProps) {
  return (
    <tr
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
        {formatDateTime(op.createdAt)}
      </td>
      <td className="p-2">
        <div className="flex flex-wrap gap-2">
          <Button
            variant="secondary"
            size="sm"
            onClick={() => onEditRoles(op)}
            data-testid={`action-edit-roles-${op.operatorId}`}
          >
            역할 변경
          </Button>
          <Button
            variant="secondary"
            size="sm"
            className={
              op.status === 'SUSPENDED'
                ? undefined
                : 'text-destructive'
            }
            onClick={() => onChangeStatus(op)}
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
            size="sm"
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
            onClick={() => onEditProfile(op)}
            data-testid={`action-edit-profile-${op.operatorId}`}
          >
            프로파일 편집
          </Button>
          {/* TASK-PC-FE-050 — org_scope (데이터-스코프) per the
              active tenant's assignment row. */}
          <Button
            variant="secondary"
            size="sm"
            onClick={() => onOrgScope(op)}
            data-testid={`action-org-scope-${op.operatorId}`}
          >
            조직 스코프
          </Button>
          {/* TASK-PC-FE-338 slice b — ADR-MONO-083 「업무 권한」: per-set
              sources + the summed effective role, revoke (DIRECT/MIGRATION
              only) + grant. Omitted (⇒ hidden) with no active tenant. */}
          {onDomainAccess && (
            <Button
              variant="secondary"
              size="sm"
              onClick={() => onDomainAccess(op)}
              data-testid={`action-domain-access-${op.operatorId}`}
            >
              업무 권한
            </Button>
          )}
          {/* TASK-PC-FE-157 — remove this operator's assignment to
              the active tenant. A home-tenant-only operator has no
              explicit assignment → producer 404 maps inline.
              TASK-MONO-784 S6 — label «배정 해제» → «이 회사에서
              내보내기» (the testid stays — no e2e/nav dependency on
              the old label). */}
          {onUnassign && (
            <Button
              variant="secondary"
              size="sm"
              className="text-destructive"
              onClick={() => onUnassign(op)}
              data-testid={`action-unassign-${op.operatorId}`}
            >
              이 회사에서 내보내기
            </Button>
          )}
        </div>
      </td>
    </tr>
  );
}
