'use client';

import { useState } from 'react';
import { Button } from '@/shared/ui/Button';
import { GroupReasonDialog } from './GroupReasonDialog';

/**
 * Add-member dialog (TASK-PC-FE-250). Collects the target `operatorId`, then
 * the reason-capture gate ({@link GroupReasonDialog}) before firing. The member
 * must belong to the group's tenant — a mismatch is the producer's
 * `422 GROUP_MEMBER_TENANT_MISMATCH` (surfaced verbatim via `error`); the
 * fan-out no-escalation re-check is the producer's `403 ROLE_GRANT_FORBIDDEN` /
 * `422 GROUP_GRANT_NO_ESCALATION`. The console does not pre-resolve the tenant
 * roster — the operatorId is entered directly and the producer stays the
 * authority.
 */
export interface GroupMemberDialogProps {
  groupName: string;
  pending: boolean;
  error: string | null;
  onConfirm: (operatorId: string, reason: string) => void;
  onCancel: () => void;
}

export function GroupMemberDialog({
  groupName,
  pending,
  error,
  onConfirm,
  onCancel,
}: GroupMemberDialogProps) {
  const [operatorId, setOperatorId] = useState('');
  const [confirming, setConfirming] = useState(false);

  const operatorIdOk = operatorId.trim().length > 0;

  return (
    <div
      className="fixed inset-0 z-40 flex items-center justify-center bg-black/50 px-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="group-member-add-title"
    >
      <div className="w-full max-w-md space-y-3 rounded-lg border border-border bg-background p-6 shadow-lg">
        <h2
          id="group-member-add-title"
          className="text-lg font-semibold text-foreground"
        >
          멤버 추가
        </h2>
        <p className="text-sm text-muted-foreground">
          「{groupName}」 그룹에 운영자를 추가합니다. 그룹의 현행 grant 가 새
          멤버에게 fan-out 됩니다 (본인 스코프 밖 grant 는 서버가 거부).
        </p>

        <div className="flex flex-col gap-1">
          <label
            htmlFor="group-member-operator"
            className="text-sm font-medium text-foreground"
          >
            운영자 ID
          </label>
          <input
            id="group-member-operator"
            value={operatorId}
            onChange={(e) => setOperatorId(e.target.value)}
            data-testid="group-member-operator-input"
            className="rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            placeholder="operator-uuid"
          />
        </div>

        {error && (
          <p
            role="alert"
            data-testid="group-member-error"
            className="text-sm text-destructive"
          >
            {error}
          </p>
        )}

        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={onCancel} disabled={pending}>
            취소
          </Button>
          <Button
            onClick={() => setConfirming(true)}
            disabled={!operatorIdOk || pending}
            data-testid="group-member-next"
          >
            다음
          </Button>
        </div>
      </div>

      {confirming && (
        <GroupReasonDialog
          title="멤버 추가"
          description={`「${groupName}」 그룹에 운영자(${operatorId.trim()})를 추가합니다.`}
          confirmLabel="추가"
          pending={pending}
          error={error}
          onConfirm={(reason) => {
            onConfirm(operatorId.trim(), reason);
            setConfirming(false);
          }}
          onCancel={() => setConfirming(false)}
        />
      )}
    </div>
  );
}
