/**
 * IAM operator overview snapshot (TASK-PC-FE-180 — the live `/iam` landing that
 * splits from the static guide, now at `/iam/guide`). Public barrel.
 */
export {
  IamOverviewScreen,
  IAM_PERFORMANCE_LABELS,
  IAM_PERFORMANCE_SCOPE_NOTE,
} from './components/IamOverviewScreen';
export { getIamOverviewState, DEFAULT_OVERVIEW_PERIOD } from './api/overview-state';
export type {
  IamOverviewState,
  OperatorsSummary,
  AccountsSummary,
  AuditSummary,
  CellStatus,
} from './api/overview-state';
