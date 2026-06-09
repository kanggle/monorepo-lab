/**
 * `features/domain-health` public API (Layered-by-Feature —
 * architecture.md § Allowed Dependencies). The app layer imports only
 * this barrel; feature internals (api/* hooks/* components/*) stay
 * encapsulated. TASK-PC-FE-013 — `console-integration-contract.md`
 * § 2.4.9.2 (Phase 7 second composition route).
 *
 * READ-ONLY (§ 2.4.9): no mutation re-exports. The hook is exported
 * for tests' sake — the screen and the page do not call it directly
 * (only `<RetryButton>` consumes it). The fetch + parse stay
 * server-side (the page composes SSR initialData via
 * `fetchDomainHealth`).
 */
export { DomainHealthScreen } from './components/DomainHealthScreen';
export type { DomainHealthScreenProps } from './components/DomainHealthScreen';
export { DomainHealthCard } from './components/DomainHealthCard';
export type { DomainHealthCardProps } from './components/DomainHealthCard';
export { DomainHealthSummaryCard } from './components/DomainHealthSummaryCard';
export type { DomainHealthSummaryCardProps } from './components/DomainHealthSummaryCard';
export { DegradeBanner, isAllDegraded } from './components/DegradeBanner';
export { RetryButton } from './components/RetryButton';
export { useDomainHealth } from './hooks/use-domain-health';
export {
  fetchDomainHealth,
  getDomainHealthState,
} from './api/domain-health-api';
export type { DomainHealthState } from './api/domain-health-api';
export {
  DomainHealthSchema,
  CardSchema,
  CARD_ORDER,
  CARD_STATUSES,
  DEGRADED_REASONS,
  HEALTH_STATUSES,
  HealthDataSchema,
} from './api/types';
export type {
  DomainHealth,
  Card,
  OkCard,
  DegradedCard,
  DomainKey,
  CardStatus,
  DegradedReason,
  HealthStatus,
  HealthData,
} from './api/types';
