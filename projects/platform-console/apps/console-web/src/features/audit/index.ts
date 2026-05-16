/**
 * `features/audit` public API (Layered-by-Feature — app/ imports only this
 * barrel, never feature internals; architecture.md § Allowed Dependencies).
 * GAP audit + security read parity, TASK-PC-FE-003 (read-only — no
 * mutation surface).
 */
export { AuditScreen } from './components/AuditScreen';
export { getAuditListState } from './api/audit-state';
export type { AuditListState } from './api/audit-state';
export type {
  AuditPage,
  AuditRow,
  AuditSource,
  AuditQueryParams,
} from './api/types';
