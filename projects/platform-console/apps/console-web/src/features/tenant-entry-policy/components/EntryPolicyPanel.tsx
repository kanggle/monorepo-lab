'use client';

import { useRef, useState } from 'react';
import { Button } from '@/shared/ui/Button';
import { ConfirmDialog } from '@/shared/ui/ConfirmDialog';
import { StatusBadge } from '@/shared/ui/StatusBadge';
import { ApiError, messageForCode } from '@/shared/api/errors';
import { formatDateTime } from '@/shared/lib/datetime';
import { useEnrolmentSummary, useSetEntryPolicy } from '../hooks/use-entry-policy';
import type { EntryPolicy } from '../api/types';

/**
 * «운영자 진입 2단계 인증» control (TASK-MONO-771 S5 — console-integration-contract
 * § 2.4.3.3, owner decisions OD-1 · OD-4). Rendered on the tenant detail page
 * and on the «보안 설정» page; the producer (`tenant.security.manage` + path
 * scope) stays the only authority — this control just reflects its answers.
 *
 * Reason-gated and confirm-gated. Turning ON carries the transition copy the
 * contract fixes (no grace period — operators without a second factor are ROUTED
 * TO ENROLMENT on their next entry, never «locked out»), the pre-check count
 * when the producer can answer it, and a self-effect warning when the current
 * session has no second factor yet.
 */

/** § 2.4.3.3 — the fixed «켜기» transition copy (OD-4). Never says «잠긴다». */
export const ENTRY_POLICY_ON_COPY =
  '켜면 이 테넌트에 운영자로 들어올 때 2단계 인증이 필요합니다. 아직 등록하지 않은 운영자는 다음 진입 때 IAM 의 등록 화면으로 안내됩니다. 이미 열린 세션은 다음 갱신 때 적용됩니다.';

export const ENTRY_POLICY_OFF_COPY =
  '끄면 이 테넌트에 운영자로 들어올 때 2단계 인증을 더 이상 요구하지 않습니다(역할에 따른 2단계 요구는 그대로입니다). 다음 진입부터 적용됩니다.';

export interface EntryPolicyPanelProps {
  tenantId: string;
  initial: EntryPolicy;
  /** `false` / `null` → the «켜기» confirm warns about the self-effect. */
  selfHasSecondFactor: boolean | null;
}

export function EntryPolicyPanel({
  tenantId,
  initial,
  selfHasSecondFactor,
}: EntryPolicyPanelProps) {
  const [policy, setPolicy] = useState<EntryPolicy>(initial);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [reason, setReason] = useState('');
  const reasonRef = useRef<HTMLTextAreaElement>(null);

  const turningOn = !policy.requireMfa;
  const summary = useEnrolmentSummary(tenantId, dialogOpen && turningOn);
  const setPolicyMutation = useSetEntryPolicy(tenantId);

  const errorMessage =
    setPolicyMutation.error instanceof ApiError
      ? messageForCode(setPolicyMutation.error.code, setPolicyMutation.error.message)
      : setPolicyMutation.error
        ? '진입 정책을 변경하지 못했습니다.'
        : null;

  function open() {
    setPolicyMutation.reset();
    setReason('');
    setDialogOpen(true);
  }

  function confirm() {
    const trimmed = reason.trim();
    if (trimmed.length === 0 || setPolicyMutation.isPending) return;
    setPolicyMutation.mutate(
      { requireMfa: turningOn, reason: trimmed },
      {
        onSuccess: (written) => {
          setPolicy(written);
          setDialogOpen(false);
        },
      },
    );
  }

  return (
    <section
      aria-labelledby="entry-policy-heading"
      data-testid="entry-policy-panel"
      className="mt-6 rounded-md border border-border p-4"
    >
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 id="entry-policy-heading" className="text-base font-semibold">
            운영자 진입 2단계 인증
          </h2>
          <p className="mt-1 text-sm text-muted-foreground">
            이 테넌트에 운영자로 들어올 때 IAM 계정의 2단계 인증(인증 앱)을 요구합니다.
            소비자 로그인과는 무관합니다.
          </p>
        </div>
        <Button
          variant={policy.requireMfa ? 'secondary' : 'primary'}
          onClick={open}
          data-testid="entry-policy-toggle"
        >
          {policy.requireMfa ? '끄기' : '켜기'}
        </Button>
      </div>
      <dl className="mt-3 grid grid-cols-2 gap-3 text-sm sm:grid-cols-3">
        <div>
          <dt className="text-muted-foreground">상태</dt>
          <dd data-testid="entry-policy-status" data-require-mfa={String(policy.requireMfa)}>
            <StatusBadge tone={policy.requireMfa ? 'success' : 'neutral'}>
              {policy.requireMfa ? '필수' : '사용 안 함'}
            </StatusBadge>
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">마지막 변경</dt>
          <dd className="text-xs" data-testid="entry-policy-updated-at">
            {policy.updatedAt ? formatDateTime(policy.updatedAt) : '—'}
          </dd>
        </div>
        <div>
          <dt className="text-muted-foreground">변경한 운영자</dt>
          <dd className="font-mono text-xs" data-testid="entry-policy-updated-by">
            {policy.updatedBy ?? '—'}
          </dd>
        </div>
      </dl>

      <ConfirmDialog
        open={dialogOpen}
        title={
          turningOn
            ? '운영자 진입에 2단계 인증을 요구할까요?'
            : '운영자 진입의 2단계 인증 요구를 끌까요?'
        }
        description={turningOn ? ENTRY_POLICY_ON_COPY : ENTRY_POLICY_OFF_COPY}
        confirmLabel={turningOn ? '켜기' : '끄기'}
        pending={setPolicyMutation.isPending}
        confirmDisabled={reason.trim().length === 0}
        errorMessage={errorMessage}
        dialogTestId="entry-policy-dialog"
        cancelTestId="entry-policy-cancel"
        confirmTestId="entry-policy-submit"
        errorTestId="entry-policy-error"
        initialFocusRef={reasonRef}
        onConfirm={confirm}
        onCancel={() => setDialogOpen(false)}
      >
        {turningOn && (
          <p className="mt-3 text-sm" data-testid="entry-policy-precheck">
            {summary.isLoading
              ? '이 테넌트 운영자의 2단계 등록 현황을 확인하는 중입니다…'
              : summary.data
                ? `이 테넌트 운영자 ${summary.data.operators}명 중 2단계 미등록 ${summary.data.notEnrolled}명은 다음 진입 때 등록 화면으로 안내됩니다.`
                : '등록 현황을 지금 확인할 수 없습니다. 미등록 운영자는 다음 진입 때 등록 화면으로 안내됩니다.'}
          </p>
        )}
        {turningOn && selfHasSecondFactor !== true && (
          <p className="mt-2 text-sm text-amber-700" data-testid="entry-policy-self-effect">
            지금 세션은 2단계 인증 없이 로그인되어 있습니다. 켜면 본인도 다음 진입 때 2단계 등록·인증을
            요청받습니다.
          </p>
        )}
        <p className="mt-2 text-xs text-muted-foreground">
          여기서 말하는 2단계는 IAM 계정의 2단계(주 경로)입니다. 관리자 비상 로그인(break-glass)의 2단계와는
          별개입니다.
        </p>
        <label
          htmlFor="entry-policy-reason"
          className="mt-4 block text-sm font-medium text-foreground"
        >
          감사 사유 <span className="text-destructive">*</span>
        </label>
        <textarea
          id="entry-policy-reason"
          ref={reasonRef}
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          rows={3}
          data-testid="entry-policy-reason"
          className="mt-1 w-full rounded-md border border-border bg-background px-3 py-2 text-sm text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          placeholder="이 변경을 하는 이유를 입력하세요"
        />
      </ConfirmDialog>
    </section>
  );
}
