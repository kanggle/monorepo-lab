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
export { getOperatorsListState } from './api/operators-state';
export type { OperatorsListState } from './api/operators-state';
export type {
  OperatorPage,
  OperatorSummary,
  OperatorStatus,
  OperatorListParams,
} from './api/types';
