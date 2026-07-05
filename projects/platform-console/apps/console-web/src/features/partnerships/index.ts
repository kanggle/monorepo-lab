/**
 * `features/partnerships` public API (Layered-by-Feature — app/ imports only
 * this barrel, never feature internals; architecture.md § Allowed
 * Dependencies). Cross-org partner delegation management surface,
 * TASK-PC-FE-187 — ADR-MONO-045 §3.4 step 3 (operator-facing partnership
 * lifecycle: host invite/list/terminate + partner accept/participant).
 */
export { PartnershipsScreen } from './components/PartnershipsScreen';
export type { PartnershipsScreenProps } from './components/PartnershipsScreen';
export { getPartnershipsListState } from './api/partnerships-state';
export type { PartnershipsListState } from './api/partnerships-state';
export type {
  Partnership,
  PartnershipList,
  PartnershipStatus,
  PartnershipMyRole,
  PartnershipListParams,
  ScopeSet,
} from './api/types';
