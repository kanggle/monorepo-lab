/**
 * `features/operator-overview` public API (Layered-by-Feature —
 * architecture.md § Allowed Dependencies). The app layer imports only
 * this barrel; feature internals (api/* hooks/* components/*) stay
 * encapsulated. TASK-PC-FE-011 — ADR-MONO-017 § D8 Phase 7 MVP.
 *
 * READ-ONLY (§ 2.4.9): no mutation re-exports. The hook is exported
 * for tests' sake — the screen and the page do not call it directly
 * (only `<RetryButton>` consumes it). The fetch + parse stay
 * server-side (the page composes SSR initialData via
 * `fetchOperatorOverview`).
 */
export { OperatorOverviewScreen } from './components/OperatorOverviewScreen';
export type { OperatorOverviewScreenProps } from './components/OperatorOverviewScreen';
export { DomainCard } from './components/DomainCard';
export type { DomainCardProps } from './components/DomainCard';
export {
  OverviewDegradeBanner,
  isAllDown,
} from './components/OverviewDegradeBanner';
export { RetryButton } from './components/RetryButton';
export { useOperatorOverview } from './hooks/use-operator-overview';
export { fetchOperatorOverview } from './api/operator-overview-api';
// 🔴 TASK-MONO-585 — SSR half in its own module (see the domain-health sibling).
export { getOperatorOverviewState } from './api/operator-overview-state';
export type { OperatorOverviewState } from './api/operator-overview-state';
export {
  OperatorOverviewSchema,
  CardSchema,
  CARD_ORDER,
  CARD_STATUSES,
  DEGRADED_REASONS,
  FORBIDDEN_REASONS,
  GapDataSchema,
  WmsDataSchema,
  ScmDataSchema,
  FinanceDataSchema,
  ErpDataSchema,
  EcommerceDataSchema,
} from './api/operator-overview-types';
export type {
  OperatorOverview,
  Card,
  OkCard,
  DegradedCard,
  ForbiddenCard,
  DomainKey,
  CardStatus,
  DegradedReason,
  ForbiddenReason,
  GapData,
  WmsData,
  ScmData,
  FinanceData,
  ErpData,
  EcommerceData,
} from './api/operator-overview-types';
