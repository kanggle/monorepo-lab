/**
 * `features/operators` public API (Layered-by-Feature — app/ imports only
 * this barrel, never feature internals; architecture.md § Allowed
 * Dependencies). GAP operators-management parity, TASK-PC-FE-004 — the
 * most privilege-sensitive Phase-2 slice (operator-privilege escalation
 * surface; per-endpoint header matrix; reason+confirm-gated mutations).
 */
export { OperatorsScreen } from './components/OperatorsScreen';
// TASK-PC-FE-045: self-service (내 비밀번호 / 내 프로필) lives on /account now.
export { AccountSelfService } from './components/AccountSelfService';
// TASK-PC-FE-050 — org_scope (데이터-스코프) dialog (composed by
// OperatorsScreen; exported for direct mounting / tests).
export { OrgScopeDialog } from './components/OrgScopeDialog';
export type { OrgScopeDialogProps } from './components/OrgScopeDialog';
export { getOperatorsListState } from './api/operators-state';
export type { OperatorsListState } from './api/operators-state';
export type {
  OperatorPage,
  OperatorSummary,
  OperatorStatus,
  OperatorListParams,
  // TASK-PC-FE-050 — org_scope assignment types.
  OperatorAssignment,
  OperatorAssignmentsResponse,
  SetOrgScopeInput,
  SetOrgScopeResult,
} from './api/types';
