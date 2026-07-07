'use client';

import { Button } from '@/shared/ui/Button';
import type { OperatorSummary } from '../api/types';
import { formatDateTime } from '@/shared/lib/datetime';
import { OperatorRoleChips, OperatorStatusBadge } from './OperatorBadges';

/**
 * A single operators-table row (TASK-PC-FE-209 split of `OperatorsTable`) —
 * the identity cells + the per-row privilege-sensitive action buttons (역할
 * 변경 / 정지·활성화 / 프로파일 편집 / 조직 스코프 / 배정 해제). Pure
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
          {/* TASK-PC-FE-157 — remove this operator's assignment to
              the active tenant. A home-tenant-only operator has no
              explicit assignment → producer 404 maps inline. */}
          {onUnassign && (
            <Button
              variant="secondary"
              size="sm"
              className="text-destructive"
              onClick={() => onUnassign(op)}
              data-testid={`action-unassign-${op.operatorId}`}
            >
              배정 해제
            </Button>
          )}
        </div>
      </td>
    </tr>
  );
}
