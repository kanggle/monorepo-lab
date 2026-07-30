/**
 * Shared label / status-vocabulary maps for the IAM overview snapshot
 * (TASK-PC-FE-180 — extracted from {@link IamOverviewScreen}, TASK-PC-FE-212
 * presentational split). Behavior-preserving: the maps + `cellPlaceholder`
 * copy are verbatim from the former god-file.
 *
 * `cellPlaceholder`/`STATUS_DOT`/`STATUS_LABEL` re-exported from
 * `shared/lib/overview-cell.ts` (TASK-PC-FE-264 — 5-domain duplication);
 * `AUDIT_SOURCE_LABEL` stays local (IAM-only vocabulary).
 */

export {
  overviewCellPlaceholder as cellPlaceholder,
  OVERVIEW_STATUS_DOT as STATUS_DOT,
  OVERVIEW_STATUS_LABEL as STATUS_LABEL,
} from '@/shared/lib/overview-cell';

export const AUDIT_SOURCE_LABEL: Record<string, string> = {
  admin: '관리 작업',
  login_history: '로그인',
  suspicious: '의심 활동',
};
