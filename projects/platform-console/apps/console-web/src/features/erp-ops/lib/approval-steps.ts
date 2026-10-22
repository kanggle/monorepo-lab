import { stepStates, type StepState } from '@/shared/ui/InlineBar';
import type { ApprovalSummary } from '../api/approval-types';

/**
 * Per-row approval step progress (TASK-PC-FE-361) — pure helper for the
 * `ApprovalScreen` list. Plain integers, no F5 money.
 *
 * Contract (`approval-api.md` v2.0 amendment): `currentStage` is the 0-BASED
 * index of the pending stage and is ABSENT once the request is finalized;
 * `totalStages` is the route length. So:
 *  - `SUBMITTED` / `IN_REVIEW` with a usable `currentStage` → position
 *    `currentStage + 1` of `totalStages` (text `n/N 단계`, the same wording as
 *    the detail dialog);
 *  - `APPROVED` → every step done (stage index is gone, but a finished route
 *    passed all of them); no text — the status badge already says it;
 *  - `REJECTED` / `WITHDRAWN` / `DRAFT` / unknown status → NO progress: where
 *    the route stopped is not on the summary, and a guessed position would
 *    read as fact;
 *  - `totalStages` missing / 0 / non-integer, or `currentStage` out of
 *    `[0, totalStages)` → nothing.
 */
export interface ApprovalStepProgress {
  states: StepState[];
  /** `null` for a finished (APPROVED) route. */
  text: string | null;
}

type StepSource = Pick<ApprovalSummary, 'status' | 'currentStage' | 'totalStages'>;

export function approvalStepProgress(r: StepSource): ApprovalStepProgress | null {
  if (r.status === 'APPROVED') {
    const states = stepStates(null, r.totalStages, true);
    return states ? { states, text: null } : null;
  }
  if (r.status !== 'SUBMITTED' && r.status !== 'IN_REVIEW') return null;
  const states = stepStates(r.currentStage, r.totalStages);
  if (!states || r.currentStage === undefined) return null;
  return { states, text: `${r.currentStage + 1}/${r.totalStages} 단계` };
}
