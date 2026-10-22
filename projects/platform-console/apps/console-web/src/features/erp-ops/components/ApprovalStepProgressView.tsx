import { StepBar } from '@/shared/ui/InlineBar';
import type { ApprovalSummary } from '../api/approval-types';
import { approvalStepProgress } from '../lib/approval-steps';

/**
 * «현재 단계 / 전체 단계» under a list row's status badge (TASK-PC-FE-361).
 * The step bar is decorative (`aria-hidden`); the position stays as text.
 * Renders nothing when the row has no honest position — see
 * {@link approvalStepProgress}. Server-safe (no hooks / `'use client'`).
 */
export function ApprovalStepProgressView({
  row,
  index,
}: {
  row: Pick<ApprovalSummary, 'status' | 'currentStage' | 'totalStages'>;
  index: number;
}) {
  const progress = approvalStepProgress(row);
  if (!progress) return null;
  return (
    <div
      className="mt-1 flex items-center gap-2"
      data-testid={`approval-steps-${index}`}
    >
      <StepBar
        states={progress.states}
        className="w-24"
        testid={`approval-steps-bar-${index}`}
      />
      {progress.text ? (
        <span
          className="text-xs text-muted-foreground"
          data-testid={`approval-steps-text-${index}`}
        >
          {progress.text}
        </span>
      ) : null}
    </div>
  );
}
