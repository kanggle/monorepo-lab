'use client';

import { useState } from 'react';
import { Button } from '@/shared/ui/Button';
import { useGrantableDomainSetsForGroup } from '../hooks/use-group-domain-sets';
import { GroupReasonDialog } from './GroupReasonDialog';

/**
 * Add-업무-권한-세트 dialog for a group (TASK-PC-FE-338 slice b — ADR-MONO-083,
 * contract § 2.4.3.4 screen 3 / rows 9-11). Pick-then-confirm, mirroring
 * `GroupGrantDialog`'s shape: a set picker (fetched from the EXISTING
 * `/api/domain-permission-sets` route — slice a; see
 * `../api/domain-sets-types.ts` file header for why this is an HTTP call,
 * not a cross-feature import) → `GroupReasonDialog` confirm.
 */
export interface GroupDomainSetDialogProps {
  groupName: string;
  pending: boolean;
  error: string | null;
  onConfirm: (setId: string, reason: string) => void;
  onCancel: () => void;
}

export function GroupDomainSetDialog({
  groupName,
  pending,
  error,
  onConfirm,
  onCancel,
}: GroupDomainSetDialogProps) {
  const options = useGrantableDomainSetsForGroup(true);
  const [setId, setSetId] = useState('');
  const [confirming, setConfirming] = useState(false);

  const picked = (options.data ?? []).find((o) => o.setId === setId) ?? null;

  return (
    <div
      className="fixed inset-0 z-40 flex items-center justify-center bg-black/50 px-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="group-domain-set-add-title"
    >
      <div className="w-full max-w-md space-y-3 rounded-lg border border-border bg-background p-6 shadow-lg">
        <h2
          id="group-domain-set-add-title"
          className="text-lg font-semibold text-foreground"
        >
          업무 권한 세트 추가
        </h2>
        <p className="text-sm text-muted-foreground">
          「{groupName}」 그룹에 업무 권한 세트를 배정합니다. 현재 멤버 전원에게
          기록(fan-out)됩니다 — 실제 업무 권한 적용은 다음 테넌트 재교환부터입니다.
        </p>

        {options.isLoading ? (
          <p className="text-sm text-muted-foreground">불러오는 중…</p>
        ) : options.isError ? (
          <p
            role="alert"
            data-testid="group-domain-set-options-error"
            className="text-sm text-destructive"
          >
            세트 목록을 불러오지 못했습니다.
          </p>
        ) : (
          <select
            value={setId}
            onChange={(e) => setSetId(e.target.value)}
            data-testid="group-domain-set-select"
            className="w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            <option value="">(세트 선택)</option>
            {(options.data ?? []).map((opt) => (
              <option key={opt.setId} value={opt.setId}>
                {opt.name}
                {opt.systemKey ? ' (시스템)' : ''} — {opt.domains.join(', ')}
              </option>
            ))}
          </select>
        )}

        {error && (
          <p
            role="alert"
            data-testid="group-domain-set-error"
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
            disabled={!setId || pending}
            data-testid="group-domain-set-next"
          >
            다음
          </Button>
        </div>
      </div>

      {confirming && (
        <GroupReasonDialog
          title="업무 권한 세트 추가"
          description={`「${groupName}」 그룹에 "${picked?.name ?? setId}" 세트를 배정합니다. 현재 멤버 전원에게 fan-out 됩니다.`}
          confirmLabel="추가"
          pending={pending}
          error={error}
          onConfirm={(reason) => {
            onConfirm(setId, reason);
            setConfirming(false);
          }}
          onCancel={() => setConfirming(false)}
        />
      )}
    </div>
  );
}
